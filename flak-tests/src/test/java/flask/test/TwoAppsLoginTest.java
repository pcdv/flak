package flask.test;

import java.io.IOException;

import flak.App;
import flak.AppFactory;
import flak.Flak;
import flak.Form;
import flak.Request;
import flak.Response;
import flak.WebServer;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.FlakLogin;
import flak.login.LoginNotRequired;
import flask.test.util.SimpleClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Check that session management is properly decoupled between two apps in
 * the same web server.
 *
 * @author pcdv
 */
public class TwoAppsLoginTest {

  private SimpleClient client;

  private WebServer ws;

  @Before
  public void setUp() throws Exception {
    AppFactory fac = Flak.getFactory();
    fac.setPort(9191);
    ws = fac.getServer();
    App app1 = fac.createApp("/app1");
    App app2 = fac.createApp("/app2");

    DefaultSessionManager dsm = new DefaultSessionManager();
    dsm.setRequireLoggedInByDefault(true);
    addLoginHandlers(app1, dsm);
    addLoginHandlers(app2, dsm);

    app1.start();
    app2.start();
    client = new SimpleClient("localhost", fac.getPort());
  }

  @After
  public void tearDown() {
    ws.stop();
  }

  private static void addLoginHandlers(App app, DefaultSessionManager dsm) {
    app.getPlugin(FlakLogin.class).setSessionManager(dsm);

    app.scan(new Object() {
      @LoginNotRequired
      @Route("/api/login")
      @Post
      public void login(Form form, Response r) {
        dsm.openSession(app, dsm.createUser("foo"), r);
      }

      @Route("/test")
      public String test() { return "OK";}

      @Route("/api/logout")
      public void logout(Request r) {
        dsm.closeCurrentSession(r);
      }
    });
  }

  @Test
  public void logIntoTwoApps() throws IOException {
    TestUtil.assertFails(() -> client.get("/app1/test"), "");
    TestUtil.assertFails(() -> client.get("/app2/test"), "");

    client.post("/app1/api/login", "");
    assertEquals("OK", client.get("/app1/test"));
    TestUtil.assertFails(() -> client.get("/app2/test"), "");

    client.post("/app2/api/login", "");
    assertEquals("OK", client.get("/app1/test"));
    assertEquals("OK", client.get("/app2/test"));

    client.get("/app1/api/logout");
    TestUtil.assertFails(() -> client.get("/app1/test"), "");
    assertEquals("OK", client.get("/app2/test"));

    client.get("/app2/api/logout");
    TestUtil.assertFails(() -> client.get("/app1/test"), "");
    TestUtil.assertFails(() -> client.get("/app2/test"), "");
  }
}
