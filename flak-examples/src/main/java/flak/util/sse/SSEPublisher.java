package flak.util.sse;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Vector;

import flak.Request;
import flak.Response;

/**
 * Dispatcher that binds to SSE clients and allows to publish events.
 */
public class SSEPublisher {

  private final Vector<SSEPipe> sessions = new Vector<>();

  /**
   * Loops waiting for events to send to associated client. Call this from
   * a HTTP handler.
   */
  public void serve(Request req) {
    Response resp = req.getResponse();
    OutputStream stream = resp.getOutputStream();

    resp.addHeader("Cache-control", "no-cache");
    resp.addHeader("Content-Type", "text/event-stream");
    resp.setStatus(200);

    SSEPipe pipe = new SSEPipe();
    sessions.add(pipe);

    try {
      stream.write(": hello\n\n".getBytes());
      stream.flush();

      while (true) {
        byte[] buf = pipe.pop();
        stream.write(buf);
        stream.flush();
        System.out.println("Written");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      sessions.remove(pipe);
    }
  }

  /**
   * Publishes an event to all connected clients.
   */
  public void publish(String event, String data) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    PrintWriter w = new PrintWriter(bout);
    w.println("event: " + event);
    w.println("data: " + data);
    w.println("\n");
    w.flush();

    byte[] bytes = bout.toByteArray();
    for (SSEPipe pipe : sessions.toArray(new SSEPipe[0])) {
      pipe.offer(bytes);
    }
  }
}
