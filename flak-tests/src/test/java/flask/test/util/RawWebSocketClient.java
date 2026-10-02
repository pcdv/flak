package flask.test.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A websocket client written by hand, for what a real one would not do: send
 * a broken handshake or frame, or go over a socket of the test's choosing.
 */
public class RawWebSocketClient implements Closeable {

  /**
   * The key of RFC 6455, section 1.3, and the answer it gets.
   */
  public static final String KEY = "dGhlIHNhbXBsZSBub25jZQ==";
  public static final String ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";

  private final Socket socket;
  private final DataInputStream in;
  private final OutputStream out;
  private final SecureRandom random = new SecureRandom();

  public RawWebSocketClient(Socket socket) throws IOException {
    this.socket = socket;
    this.in = new DataInputStream(socket.getInputStream());
    this.out = socket.getOutputStream();
  }

  /**
   * Sends a handshake for specified path, with the version of the protocol
   * given, and returns the head of the response.
   *
   * @param headers more header lines, e.g. "Sec-WebSocket-Protocol: chat"
   */
  public String handshake(String path, String version, String... headers) throws IOException {
    StringBuilder request = new StringBuilder();
    request.append("GET ").append(path).append(" HTTP/1.1\r\n")
           .append("Host: localhost\r\n")
           .append("Upgrade: websocket\r\n")
           .append("Connection: Upgrade\r\n")
           .append("Sec-WebSocket-Key: ").append(KEY).append("\r\n")
           .append("Sec-WebSocket-Version: ").append(version).append("\r\n");
    for (String h : headers)
      request.append(h).append("\r\n");
    request.append("\r\n");
    out.write(request.toString().getBytes(US_ASCII));
    out.flush();

    ByteArrayOutputStream head = new ByteArrayOutputStream();
    while (!head.toString(US_ASCII).endsWith("\r\n\r\n"))
      head.write(in.readUnsignedByte());
    return head.toString(US_ASCII);
  }

  /**
   * Sends a final frame with a payload shorter than 126 bytes.
   */
  public void send(int opcode, String payload, boolean masked) throws IOException {
    byte[] data = payload.getBytes(UTF_8);
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.write(0x80 | opcode);
    frame.write((masked ? 0x80 : 0) | data.length);
    if (masked) {
      byte[] mask = new byte[4];
      random.nextBytes(mask);
      frame.write(mask);
      for (int i = 0; i < data.length; i++)
        data[i] ^= mask[i & 3];
    }
    frame.write(data);
    out.write(frame.toByteArray());
    out.flush();
  }

  /**
   * Reads a frame of the server, which is never masked, and returns its
   * opcode and payload, e.g. "1 hello". A close frame is shown with its
   * code, e.g. "8 1000 bye".
   */
  public String read() throws IOException {
    int b0 = in.readUnsignedByte();
    int len = in.readUnsignedByte();
    if (len == 126)
      len = in.readUnsignedShort();
    else if (len == 127)
      len = (int) in.readLong();
    byte[] payload = new byte[len];
    in.readFully(payload);

    int opcode = b0 & 0x0F;
    if (opcode == 8 && len >= 2) {
      int code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
      return "8 " + code + " " + new String(payload, 2, len - 2, UTF_8);
    }
    return opcode + " " + new String(payload, UTF_8);
  }

  /**
   * Tells whether the server closed the connection.
   */
  public boolean isAtEOF() throws IOException {
    return in.read() == -1;
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
