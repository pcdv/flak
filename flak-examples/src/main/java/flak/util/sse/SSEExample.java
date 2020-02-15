package flak.util.sse;

import java.io.InputStream;

import flak.App;
import flak.Flak;
import flak.Request;
import flak.annotations.Route;

/**
 * Server-Sent Events example.
 * <p>
 * https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
 */
public class SSEExample {

  private SSEPublisher mgr;

  public SSEExample(SSEPublisher mgr) {
    this.mgr = mgr;
  }

  @Route("/sse")
  public void sse(Request req) {
    mgr.serve(req);
  }

  @Route("/")
  public InputStream index() {
    return getClass().getResourceAsStream("/sse/index.html");
  }

  public static void main(String[] args) throws Exception {
    App app = Flak.createHttpApp(8080);
    SSEPublisher mgr = new SSEPublisher();
    app.scan(new SSEExample(mgr));
    app.start();

    while (true) {
      Thread.sleep(5000);
      System.out.println("Publishing time...");
      mgr.publish("ping", "{\"time\":" + System.currentTimeMillis() + "}");
    }
  }
}
