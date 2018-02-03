package flask.test;

import java.nio.file.Files;

import flak.App;
import flak.AppFactory;
import flak.Flak;
import flak.Request;
import flak.WebServer;
import flak.annotations.Route;
import flask.test.util.SimpleClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

/**
 * Tests a single WebServer shared by two separate apps.
 *
 * @author pcdv
 */
public class TwoAppsTest {

  private SimpleClient client;

  private WebServer ws;

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private App app1, app2;

  @Before
  public void setUp() throws Exception {
    AppFactory fac = Flak.getFactory();
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

    app2 = fac.createApp("/app2").scan(new Object() {
      @Route("/hello")
      public String hello(Request req) {
        return "Hello from app2 - path = "+req.getPath();
      }
    });

    app1.start();
    app2.start();

    client = new SimpleClient("localhost", fac.getPort());
  }

  @After
  public void tearDown() throws Exception {
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
    app1.serveDir("/stuff", tmp.getRoot());
    assertEquals("Foobar", client.get("/app1/stuff/foo"));
  }

  @Test
  public void testWithArg() throws Exception {
    assertEquals("OK:foo", client.get("/app1/foo/arg"));


  }
}
