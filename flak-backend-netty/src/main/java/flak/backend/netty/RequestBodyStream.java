package flak.backend.netty;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * The body of a request, as it arrives: the event loop offers the chunks it
 * decodes, and the thread running the route handler reads them. A handler can
 * therefore pipe a body of any size somewhere without it ever being held in
 * memory as a whole.
 * <p>
 * When more than {@link #HIGH_WATER} bytes are waiting to be read, the channel
 * stops being read until the handler catches up: without that, a handler
 * slower than the network, e.g. one writing to a disk, would end up buffering
 * the whole upload after all.
 *
 * @author pcdv
 */
class RequestBodyStream extends InputStream {

  /**
   * Marks the end of the body in the queue. Cannot be a ByteBuf, an empty one
   * being a perfectly normal chunk.
   */
  private static final Object EOF = new Object();

  private static final long HIGH_WATER = 256 * 1024;

  private final BlockingQueue<Object> chunks = new LinkedBlockingQueue<>();

  private final Channel channel;

  private final AtomicLong queued = new AtomicLong();

  /** The chunk being read, held until it is exhausted. */
  private ByteBuf current;

  private boolean done;

  private volatile boolean discarded;

  private volatile IOException failure;

  RequestBodyStream(Channel channel) {
    this.channel = channel;
  }

  // ------------------------------------------------------------------
  // called from the event loop
  // ------------------------------------------------------------------

  /**
   * Hands a chunk over to the reader. The caller keeps its own reference: what
   * is queued here is retained separately and released once read.
   */
  void offer(ByteBuf content) {
    if (discarded)
      return;

    content.retain();
    if (queued.addAndGet(content.readableBytes()) > HIGH_WATER)
      channel.config().setAutoRead(false);
    chunks.add(content);
  }

  void complete() {
    chunks.add(EOF);
  }

  /**
   * Wakes up a reader that would otherwise wait for a body that will never
   * arrive, e.g. because the connection died.
   */
  void fail(Throwable cause) {
    if (failure == null)
      failure = cause instanceof IOException
        ? (IOException) cause
        : new IOException(cause);
    chunks.add(EOF);
  }

  /**
   * Releases what the handler did not read. Anything offered afterwards is
   * dropped, so that ignoring a body cannot leak it.
   */
  void discard() {
    discarded = true;
    if (current != null) {
      current.release();
      current = null;
    }
    for (Object o = chunks.poll(); o != null; o = chunks.poll()) {
      if (o != EOF)
        ((ByteBuf) o).release();
    }
  }

  // ------------------------------------------------------------------
  // called from the thread running the route handler
  // ------------------------------------------------------------------

  @Override
  public int read() throws IOException {
    ByteBuf b = current();
    if (b == null)
      return -1;
    int v = b.readUnsignedByte();
    consumed(1);
    return v;
  }

  @Override
  public int read(byte[] dst, int off, int len) throws IOException {
    if (len == 0)
      return 0;
    ByteBuf b = current();
    if (b == null)
      return -1;
    int n = Math.min(len, b.readableBytes());
    b.readBytes(dst, off, n);
    consumed(n);
    return n;
  }

  @Override
  public int available() {
    return current == null ? 0 : current.readableBytes();
  }

  private ByteBuf current() throws IOException {
    while (current == null || !current.isReadable()) {
      if (current != null) {
        current.release();
        current = null;
      }

      if (done)
        return endOfBody();

      Object next;
      try {
        next = chunks.take();
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while reading the request body", e);
      }

      if (next == EOF) {
        done = true;
        return endOfBody();
      }

      current = (ByteBuf) next;
    }
    return current;
  }

  private ByteBuf endOfBody() throws IOException {
    if (failure != null)
      throw failure;
    return null;
  }

  private void consumed(int n) {
    // resume reading well before the queue is empty, so that the handler does
    // not have to wait for the network on every chunk
    if (queued.addAndGet(-n) <= HIGH_WATER / 2 && !channel.config().isAutoRead())
      channel.config().setAutoRead(true);
  }
}
