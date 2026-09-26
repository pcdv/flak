package flask.test;

import flak.App;
import flak.AppFactory;
import flak.annotations.Route;
import flask.test.util.SimpleClient;
import org.junit.After;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Checks that every request under the path of an app reaches it, even one
 * that shares nothing with its routes: the JDK backend used to leave those
 * to the embedded HttpServer, which answered its own 404.
 */
public class UnknownPageWithoutRootRouteTest extends AbstractAppTest {

  private App app2;

  @Route("/api/x")
  public String x() {
    return "X";
  }

  @After
  public void stopApp2() {
    if (app2 != null)
      app2.stop();
  }

  @Test
  public void testUnknownPageHandler() throws Exception {
    app.setUnknownPageHandler(r -> {
      r.getResponse().setStatus(200);
      r.getResponse().getOutputStream().write(("gotcha " + r.getPath()).getBytes());
    });

    assertEquals("X", client.get("/api/x"));
    assertEquals("gotcha /api/y", client.get("/api/y"));
    assertEquals("gotcha /foo", client.get("/foo"));
    assertEquals("gotcha /", client.get("/"));
  }

  @Test
  public void testDefault404() throws Exception {
    AtomicInteger hooks = new AtomicInteger();
    app.addBeforeAllHook(r -> hooks.incrementAndGet());

    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + "/foo").openConnection();
    assertEquals(404, con.getResponseCode());
    assertEquals("text/plain", con.getContentType());
    assertEquals("Not found", new String(con.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
    assertEquals(1, hooks.get());
  }

  /**
   * Same for an app hosted at a path: what is under it reaches it, what is
   * not stays out of it.
   */
  @Test
  public void testAppWithPath() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    app2 = factory.createApp("/app2");
    app2.scan(this);
    app2.setUnknownPageHandler(r -> {
      r.getResponse().setStatus(200);
      r.getResponse().getOutputStream().write(("app2 " + r.getPath()).getBytes());
    });
    app2.start();

    SimpleClient c = new SimpleClient("localhost", factory.getPort());
    assertEquals("X", c.get("/app2/api/x"));
    assertEquals("app2 /foo", c.get("/app2/foo"));
    assertEquals("app2 /", c.get("/app2/"));
    TestUtil.assertFails(() -> c.get("/other"), "404");
  }
}
