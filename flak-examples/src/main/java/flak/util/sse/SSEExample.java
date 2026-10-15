package flak.util.sse;

import java.io.InputStream;

import flak.App;
import flak.Flak;
import flak.Request;
import flak.annotations.Route;
import flak.util.SseBroadcaster;
import flak.util.SseEvent;

/**
 * Server-Sent Events example: open http://localhost:8080/ and click Connect.
 * <p>
 * https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
 */
public class SSEExample {

  private final SseBroadcaster events;

  public SSEExample(SseBroadcaster events) {
    this.events = events;
  }

  @Route("/sse")
  public void sse(Request req) {
    events.serve(req);
  }

  @Route("/")
  public InputStream index() {
    return getClass().getResourceAsStream("/sse/index.html");
  }

  public static void main(String[] args) throws Exception {
    App app = Flak.createHttpApp(8080);
    SseBroadcaster events = new SseBroadcaster();
    app.scan(new SSEExample(events));
    app.start();

    while (true) {
      Thread.sleep(5000);
      events.publish(SseEvent.data("{\"time\":" + System.currentTimeMillis() + "}")
                             .name("ping"));
    }
  }
}
