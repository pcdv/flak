package flask.test;

import flak.App;
import flak.AppFactory;
import flak.Request;
import flak.WebServer;
import flak.annotations.Route;
import flak.plugin.resource.FlakResourceImpl;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

/**
 * Tests a single WebServer shared by two separate apps.
 *
 * @author pcdv
 */
public class TwoAppsTest {

  @Rule
  public ThreadState.ThreadStateRule noZombies = new ThreadState.ThreadStateRule();

  private SimpleClient client;

  private WebServer ws;

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private App app1;

  @Before
  public void setUp() throws Exception {
    AppFactory fac = TestUtil.getFactory();
    fac.setPort(9191);
    ws = fac.getServer();

    app1 = fac.createApp("/app1").scan(new Object() {
      @Route("/hello")
      public String hello() {
        return "Hello from app1";
      }

      @Route("/:id/arg")
      public String test(String id) {
        return "OK:" + id;
      }
    });

    App app2 = fac.createApp("/app2").scan(new Object() {
      @Route("/hello")
      public String hello(Request req) {
        return "Hello from app2 - path = " + req.getPath();
      }
    });

    app1.start();
    app2.start();

    client = new SimpleClient("localhost", fac.getPort());
  }

  @After
  public void tearDown() {
    if (ws != null)
      ws.stop();
  }

  @Test
  public void testMethodHandlers() throws Exception {
    assertEquals("Hello from app1", client.get("/app1/hello"));
    assertEquals("Hello from app2 - path = /hello", client.get("/app2/hello"));
  }

  @Test
  public void testServeDir() throws Exception {
    Files.write(tmp.newFile("foo").toPath(), "Foobar".getBytes());
    new FlakResourceImpl(app1).serveDir("/stuff", tmp.getRoot());
    assertEquals("Foobar", client.get("/app1/stuff/foo"));
  }

  @Test
  public void testWithArg() throws Exception {
    assertEquals("OK:foo", client.get("/app1/foo/arg"));
  }
}
