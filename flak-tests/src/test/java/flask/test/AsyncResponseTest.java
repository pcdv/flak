package flask.test;

import java.io.IOException;

import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Misc POST tests.
 */
public class AsyncResponseTest extends AbstractAppTest {

  @Route("/sync")
  public void replySync(Response r) throws IOException {
    reply(r);
  }

  private void reply(Response resp) throws IOException {
    resp.setStatus(200);
    resp.getOutputStream().write("foobar".getBytes());
    // NB: this does nothing now (since it would mess up the response, the output
    // stream is closed automatically at the end). Leave it for non regression, in
    // case some pre-existing handlers do that.
    resp.getOutputStream().close();
  }

  @Route("/async")
  public Response replyAsync() {
    final Response r = app.getResponse();
    new Thread(() -> {
      try {
        reply(r);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }).start();
    return r;
  }

  @Test
  public void testSync() throws Exception {
    Assert.assertEquals("foobar", client.get("/sync"));
  }

  /**
   * Replying from a thread other than the one running the handler is not
   * supported, by any backend: as soon as the handler returns, the response is
   * considered complete and is sent. The HttpServer of the JDK could not do it
   * anyway, its exchange belongs to the handler thread, and the netty backend,
   * which writes the response from whichever thread holds it, has no way of
   * being told to wait.
   * <p>
   * Supporting it would take an explicit annotation on the handler, so that a
   * backend knows not to finish the response when the handler returns, and a
   * way for the application to signal that it is done, closing the output
   * stream having no effect today.
   */
  @Ignore
  @Test
  public void testAsync() throws Exception {
    Assert.assertEquals("foobar", client.get("/async"));
  }
}
