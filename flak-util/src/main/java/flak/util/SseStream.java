package flak.util;

import java.io.IOException;
import java.io.OutputStream;

import flak.Request;
import flak.Response;

/**
 * The Server-Sent Events stream of one client, opened by a handler that then
 * sends events for as long as it runs:
 * <pre>
 * &#64;Route("/progress")
 * public void progress(Request req) throws Exception {
 *   SseStream stream = SseStream.open(req);
 *   for (Step step : job.steps())
 *     stream.send(SseEvent.data(step.describe()).name("step"));
 * }
 * </pre>
 * The stream ends when the handler returns. A browser then reconnects, after
 * a few seconds, unless the page closes its <code>EventSource</code>.
 * <p>
 * Methods can be called from any thread, until the handler returns.
 * {@link SseBroadcaster} sends the same events to every connected client.
 *
 * @since 3.5.0
 */
public class SseStream {

  private final Request request;

  private final OutputStream out;

  private volatile boolean open = true;

  private SseStream(Request request) {
    this.request = request;
    this.out = request.getResponse().getOutputStream();
  }

  /**
   * Sends the headers of an event stream, so that the browser's
   * <code>EventSource</code> fires its open event right away.
   *
   * @throws IOException if the client is already gone
   */
  public static SseStream open(Request request) throws IOException {
    Response resp = request.getResponse();
    resp.setStatus(200);
    resp.addHeader("Content-Type", "text/event-stream; charset=utf-8");
    resp.addHeader("Cache-Control", "no-cache");
    SseStream stream = new SseStream(request);
    // the headers go out with the first bytes of the body
    stream.comment("");
    return stream;
  }

  /**
   * Returns the Last-Event-ID header: the id of the last event received by a
   * browser that reconnects, i.e. where to resume. Null on a first connection,
   * or when no event had an id.
   */
  public String getLastEventId() {
    return request.getHeader("Last-Event-ID");
  }

  public Request getRequest() {
    return request;
  }

  /**
   * Sends an event to the client.
   *
   * @throws IOException if the client is gone, after which the stream is
   *                     closed
   */
  public void send(SseEvent event) throws IOException {
    write(event.encode());
  }

  /**
   * Sends a comment, which the browser ignores, e.g. to keep the connection
   * from being closed by proxies, or to find out whether the client is still
   * there: the write fails, not necessarily at once, once it is gone.
   */
  public void comment(String text) throws IOException {
    write(SseEvent.comment(text));
  }

  /**
   * Returns false once a write failed. The client may be gone before that:
   * nothing tells, until something is written.
   */
  public boolean isOpen() {
    return open;
  }

  synchronized void write(byte[] bytes) throws IOException {
    if (!open)
      throw new IOException("Stream closed");
    try {
      out.write(bytes);
      out.flush();
    }
    catch (IOException | RuntimeException e) {
      open = false;
      throw e;
    }
  }
}
