package flak.websocket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import flak.Request;
import flak.spi.util.Log;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A websocket served on the connection of an HttpServer exchange: the framing
 * of RFC 6455, without extensions. The frames of the client are read by the
 * thread of the exchange, which also runs the callbacks of the endpoint, so
 * that they are never called concurrently for a same websocket. Frames are
 * sent by whichever thread calls send(), and each send blocks until its frame
 * is handed to the socket.
 * <p>
 * The watchdog of the endpoint checks that the connection is alive, see
 * {@link #checkAlive(long)}: it is the only way out for a thread stuck reading
 * or writing a connection whose client vanished without closing it.
 *
 * @author pcdv
 */
final class Connection implements WebSocket {

  private static final int OP_CONTINUATION = 0x0;
  private static final int OP_TEXT = 0x1;
  private static final int OP_BINARY = 0x2;
  private static final int OP_CLOSE = 0x8;
  private static final int OP_PING = 0x9;
  private static final int OP_PONG = 0xA;

  private static final byte[] EMPTY = {};

  /**
   * The biggest array the JVM is sure to allocate.
   */
  private static final long MAX_ARRAY = Integer.MAX_VALUE - 8;

  /**
   * The size of the slices frames are written in, so that a big message sent
   * to a slow client shows progress, rather than a write that seems stuck.
   */
  private static final int WRITE_CHUNK = 64 * 1024;

  private enum State {OPEN, CLOSING, CLOSED}

  private final WebSocketEndpoint endpoint;

  private final HttpExchange exchange;

  private final DataInputStream in;

  private final OutputStream out;

  private final SocketChannel channel;

  private final long maxMessageSize;

  /**
   * The subprotocol agreed in the handshake, or null.
   */
  private final String protocol;

  /**
   * When the client last sent anything, as of System.nanoTime().
   */
  private volatile long lastReceived = System.nanoTime();

  /**
   * When the slice of frame being written started to be, or 0 when nothing is
   * being written.
   */
  private volatile long writeStarted;

  /**
   * Why the connection was dropped on our side, if it was.
   */
  private volatile String abortReason;

  /**
   * Serializes the frames sent, and the changes of state with the close code
   * and reason that go with them.
   */
  private final Object lock = new Object();

  private volatile State state = State.OPEN;

  /**
   * What onClose() reports: the code of whichever side started the closing
   * handshake, or ABNORMAL_CLOSE when the connection dropped without one.
   */
  private int closeCode = CloseFrame.ABNORMAL_CLOSE;

  private String closeReason = "";

  private boolean closedByClient = true;

  private volatile Object attachment;

  Connection(WebSocketEndpoint endpoint,
             HttpExchange exchange,
             RawStreams streams,
             long maxMessageSize,
             String protocol,
             Object attachment) {
    this.endpoint = endpoint;
    this.exchange = exchange;
    // the stream the request headers were read from is buffered already
    this.in = new DataInputStream(new Progress(streams.in));
    this.out = streams.out;
    this.channel = streams.channel;
    this.maxMessageSize = maxMessageSize;
    this.protocol = protocol;
    this.attachment = attachment;
  }

  /**
   * Accepts the upgrade. Written straight to the connection: HttpServer
   * refuses to send a 1xx without ending the exchange, and would then read
   * the frames of the client as the next HTTP request.
   */
  void sendHandshake(String acceptKey) throws IOException {
    String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                      "Upgrade: websocket\r\n" +
                      "Connection: Upgrade\r\n" +
                      "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                      (protocol == null ? "" : "Sec-WebSocket-Protocol: " + protocol + "\r\n") +
                      "\r\n";
    out.write(response.getBytes(US_ASCII));
    out.flush();
  }

  void open(Request handshake) {
    invoke(() -> endpoint.onOpen(this, handshake));
  }

  /**
   * Reads and dispatches the frames of the client until the websocket is
   * closed, then closes the connection.
   */
  void run() {
    try {
      readFrames();
    }
    catch (ProtocolError e) {
      fail(e.code, e.getMessage());
    }
    catch (IOException e) {
      // dropped by the client, by abort(), or by the server being stopped.
      // If our close frame was already out, the client merely hung up
      // instead of answering it: the closure stays ours
      synchronized (lock) {
        if (state == State.OPEN) {
          String reason = abortReason;
          closeReason = reason != null ? reason : Objects.toString(e.getMessage(), "");
          closedByClient = reason == null;
        }
      }
    }
    finally {
      synchronized (lock) {
        state = State.CLOSED;
      }
      // the response was never started, so HttpServer closes the connection
      exchange.close();
    }
  }

  void notifyClose() {
    invoke(() -> endpoint.onClose(this, closeCode, closeReason, closedByClient));
  }

  private void readFrames() throws IOException {
    // the fragments of the message being received, if any, and its opcode
    ByteArrayOutputStream fragments = null;
    int messageType = 0;

    while (true) {
      int b0 = in.readUnsignedByte();
      int b1 = in.readUnsignedByte();
      boolean fin = (b0 & 0x80) != 0;
      int opcode = b0 & 0x0F;

      if ((b0 & 0x70) != 0)
        throw new ProtocolError(CloseFrame.PROTOCOL_ERROR, "Reserved bits set");
      if ((b1 & 0x80) == 0)
        throw new ProtocolError(CloseFrame.PROTOCOL_ERROR, "Unmasked frame");

      long length = b1 & 0x7F;
      if (length == 126)
        length = in.readUnsignedShort();
      else if (length == 127)
        length = in.readLong();

      switch (opcode) {
      case OP_CLOSE:
      case OP_PING:
      case OP_PONG:
        if (!fin || length > 125)
          throw new ProtocolError(CloseFrame.PROTOCOL_ERROR,
                                  "Fragmented or oversized control frame");
        break;
      case OP_TEXT:
      case OP_BINARY:
        if (fragments != null)
          throw new ProtocolError(CloseFrame.PROTOCOL_ERROR,
                                  "Expected a continuation frame");
        checkSize(length);
        break;
      case OP_CONTINUATION:
        if (fragments == null)
          throw new ProtocolError(CloseFrame.PROTOCOL_ERROR,
                                  "Unexpected continuation frame");
        checkSize(fragments.size() + length);
        break;
      default:
        throw new ProtocolError(CloseFrame.PROTOCOL_ERROR,
                                "Unknown opcode " + opcode);
      }

      byte[] mask = new byte[4];
      in.readFully(mask);
      byte[] payload = new byte[(int) length];
      in.readFully(payload);
      for (int i = 0; i < payload.length; i++)
        payload[i] ^= mask[i & 3];

      switch (opcode) {
      case OP_CLOSE:
        onCloseFrame(payload);
        return;
      case OP_PING:
        synchronized (lock) {
          if (state == State.OPEN)
            writeFrame(OP_PONG, payload);
        }
        break;
      case OP_PONG:
        break;
      case OP_CONTINUATION:
        fragments.write(payload);
        if (fin) {
          deliver(messageType, fragments.toByteArray());
          fragments = null;
        }
        break;
      default:
        if (fin)
          deliver(opcode, payload);
        else {
          fragments = new ByteArrayOutputStream();
          fragments.write(payload);
          messageType = opcode;
        }
      }
    }
  }

  /**
   * Checks the size a message will have once its next frame is read, before
   * allocating anything for it. A negative length is a 64 bit one that
   * overflowed.
   */
  private void checkSize(long size) throws ProtocolError {
    long limit = maxMessageSize >= 0 ? Math.min(maxMessageSize, MAX_ARRAY) : MAX_ARRAY;
    if (size < 0 || size > limit)
      throw new ProtocolError(CloseFrame.TOOBIG, "Message bigger than " + limit + " bytes");
  }

  private void deliver(int opcode, byte[] payload) throws ProtocolError {
    // once close() is called, the application is done with the websocket
    if (state != State.OPEN)
      return;

    if (opcode == OP_TEXT) {
      String text = decode(payload, 0);
      invoke(() -> endpoint.onMessage(this, text));
    }
    else {
      invoke(() -> endpoint.onMessage(this, ByteBuffer.wrap(payload)));
    }
  }

  private void onCloseFrame(byte[] payload) throws ProtocolError {
    int code = CloseFrame.NOCODE;
    String reason = "";

    if (payload.length == 1)
      throw new ProtocolError(CloseFrame.PROTOCOL_ERROR, "Truncated close code");
    if (payload.length >= 2) {
      code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
      if (!CloseFrame.canBeSent(code))
        throw new ProtocolError(CloseFrame.PROTOCOL_ERROR, "Invalid close code " + code);
      reason = decode(payload, 2);
    }

    synchronized (lock) {
      // otherwise, it is the answer to our own close frame
      if (state == State.OPEN) {
        state = State.CLOSING;
        closeCode = code;
        closeReason = reason;
        closedByClient = true;
        try {
          // echo the code, if any, as is customary
          writeFrame(OP_CLOSE, code == CloseFrame.NOCODE ? EMPTY : closePayload(code, EMPTY));
        }
        catch (IOException ignored) {
          // the client is gone already, nothing more to tell it
        }
      }
    }
  }

  /**
   * Fails the websocket after a violation of the protocol by the client:
   * tells it why, and closes without waiting for its answer.
   */
  private void fail(int code, String reason) {
    synchronized (lock) {
      if (state == State.OPEN) {
        state = State.CLOSING;
        closeCode = code;
        closeReason = reason;
        closedByClient = false;
        try {
          writeFrame(OP_CLOSE, closePayload(code, reason.getBytes(UTF_8)));
        }
        catch (IOException ignored) {
          // closing anyway
        }
      }
    }
  }

  private static String decode(byte[] payload, int offset) throws ProtocolError {
    try {
      // unlike new String(), a fresh decoder reports malformed input
      return UTF_8.newDecoder()
                  .decode(ByteBuffer.wrap(payload, offset, payload.length - offset))
                  .toString();
    }
    catch (CharacterCodingException e) {
      throw new ProtocolError(CloseFrame.NO_UTF8, "Invalid UTF-8");
    }
  }

  // /////////// WebSocket methods

  @Override
  public void send(String text) {
    sendFrame(OP_TEXT, text.getBytes(UTF_8));
  }

  @Override
  public void send(byte[] data) {
    sendFrame(OP_BINARY, data);
  }

  @Override
  public void send(ByteBuffer data) {
    byte[] bytes = new byte[data.remaining()];
    data.duplicate().get(bytes);
    sendFrame(OP_BINARY, bytes);
  }

  @Override
  public void sendPing() {
    sendFrame(OP_PING, EMPTY);
  }

  void sendText(byte[] utf8) {
    sendFrame(OP_TEXT, utf8);
  }

  private void sendFrame(int opcode, byte[] payload) {
    synchronized (lock) {
      if (state != State.OPEN)
        throw new IllegalStateException("WebSocket is " + state.name().toLowerCase());
      try {
        writeFrame(opcode, payload);
      }
      catch (IOException e) {
        // half a frame may be out, nothing else can be sent after it
        abort("Send failed: " + e);
        throw new UncheckedIOException(e);
      }
    }
  }

  @Override
  public void close() {
    close(CloseFrame.NORMAL);
  }

  @Override
  public void close(int code) {
    close(code, "");
  }

  @Override
  public void close(int code, String message) {
    if (!CloseFrame.canBeSent(code))
      throw new IllegalArgumentException("Close code cannot be sent: " + code);
    String reason = message == null ? "" : message;
    byte[] bytes = reason.getBytes(UTF_8);
    if (bytes.length > 123)
      throw new IllegalArgumentException("Close message longer than 123 bytes");

    synchronized (lock) {
      if (state != State.OPEN)
        return;
      state = State.CLOSING;
      closeCode = code;
      closeReason = reason;
      closedByClient = false;
      try {
        writeFrame(OP_CLOSE, closePayload(code, bytes));
      }
      catch (IOException e) {
        abort("Send failed: " + e);
      }
    }
  }

  /**
   * Drops the connection, which makes the threads reading or writing it give
   * up. Never blocks, so that the watchdog can call it while another thread is
   * stuck in a write, holding the lock.
   */
  private void abort(String reason) {
    if (abortReason == null)
      abortReason = reason;
    try {
      channel.close();
    }
    catch (IOException ignored) {
      // dropped all the same
    }
  }

  /**
   * Drops the connection if it looks dead: a write stuck for longer than
   * specified timeout, i.e. a client that stopped reading, or nothing received
   * for one and a half times the timeout, despite the pings of
   * {@link #pingIfIdle(long)}. Called by the watchdog of the endpoint.
   */
  void checkAlive(long timeout) {
    long now = System.nanoTime();
    long started = writeStarted;
    if (started != 0 && now - started > timeout)
      abort("Connection lost: the client stopped reading");
    else if (now - lastReceived > timeout + timeout / 2)
      abort("Connection lost: no answer to pings");
  }

  /**
   * Pings the client if it has been silent for specified time, so that a
   * client that is alive but has nothing to say shows it. Called by the
   * watchdog of the endpoint.
   */
  void pingIfIdle(long idle) {
    if (state == State.OPEN && System.nanoTime() - lastReceived >= idle) {
      try {
        sendPing();
      }
      catch (RuntimeException ignored) {
        // closing in the meantime, or broken and dropped
      }
    }
  }

  private static byte[] closePayload(int code, byte[] reason) {
    byte[] payload = new byte[2 + reason.length];
    payload[0] = (byte) (code >>> 8);
    payload[1] = (byte) code;
    System.arraycopy(reason, 0, payload, 2, reason.length);
    return payload;
  }

  /**
   * Writes a frame in a single call, since the stream of HttpServer is not
   * buffered. Frames sent by a server are not masked.
   */
  private void writeFrame(int opcode, byte[] payload) throws IOException {
    int len = payload.length;
    int header = len < 126 ? 2 : len <= 0xFFFF ? 4 : 10;
    byte[] frame = new byte[header + len];
    frame[0] = (byte) (0x80 | opcode);
    if (len < 126) {
      frame[1] = (byte) len;
    }
    else if (len <= 0xFFFF) {
      frame[1] = 126;
      frame[2] = (byte) (len >>> 8);
      frame[3] = (byte) len;
    }
    else {
      frame[1] = 127;
      for (int i = 0; i < 8; i++)
        frame[2 + i] = (byte) ((long) len >>> (56 - 8 * i));
    }
    System.arraycopy(payload, 0, frame, header, len);
    try {
      for (int off = 0; off < frame.length; off += WRITE_CHUNK) {
        writeStarted = System.nanoTime();
        out.write(frame, off, Math.min(WRITE_CHUNK, frame.length - off));
      }
      out.flush();
    }
    finally {
      writeStarted = 0;
    }
  }

  /**
   * Runs a callback of the endpoint, passing whatever it throws to onError().
   */
  private void invoke(Runnable callback) {
    try {
      callback.run();
    }
    catch (RuntimeException e) {
      try {
        endpoint.onError(this, e);
      }
      catch (RuntimeException e2) {
        Log.error(e2, e2);
      }
    }
  }

  @Override
  public boolean isOpen() {
    return state == State.OPEN;
  }

  @Override
  public boolean isClosing() {
    return state == State.CLOSING;
  }

  @Override
  public boolean isClosed() {
    return state == State.CLOSED;
  }

  @Override
  public InetSocketAddress getRemoteSocketAddress() {
    return exchange.getRemoteAddress();
  }

  @Override
  public InetSocketAddress getLocalSocketAddress() {
    return exchange.getLocalAddress();
  }

  @Override
  public String getResourceDescriptor() {
    return exchange.getRequestURI().toString();
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getAttachment() {
    return (T) attachment;
  }

  @Override
  public <T> void setAttachment(T attachment) {
    this.attachment = attachment;
  }

  @Override
  public String toString() {
    return "WebSocket[" + getRemoteSocketAddress() + " " + getResourceDescriptor() + "]";
  }

  /**
   * Notes when the client last sent anything, however little.
   */
  private final class Progress extends FilterInputStream {
    Progress(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      lastReceived = System.nanoTime();
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = super.read(b, off, len);
      lastReceived = System.nanoTime();
      return n;
    }
  }

  /**
   * A violation of the protocol by the client, and the code to close with.
   */
  private static final class ProtocolError extends IOException {
    final int code;

    ProtocolError(int code, String message) {
      super(message);
      this.code = code;
    }
  }
}
