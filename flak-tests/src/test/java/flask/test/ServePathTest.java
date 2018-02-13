package flask.test;

import flak.App;
import flak.AppFactory;
import flak.Flak;
import flak.WebServer;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.FlakLogin;
import flask.test.util.SimpleClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class ServePathTest {

  private App app;

  private DefaultSessionManager sessionManager;

  @After
  public void tearDown() {
    app.stop();
  }

  @Route("/test")
  public String test() {
    return "OK";
  }

  /**
   * Reproduce bug with handler registered twice in server when server started
   * before app.
   */
  @Test
  public void testStartServerBeforeServePath() throws Exception {
    AppFactory fac = Flak.getFactory();
    fac.setPort(9191);
    WebServer ws = fac.getServer();
    ws.start();
    app = fac.createApp("/app");
    app.servePath("/static", "/test-resources");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("FOO", client.get("/static/foo.html"));
  }

  @Test
  public void testRedirectLoginToResource() throws Exception {
    AppFactory factory = Flak.getFactory();
    factory.setPort(9191);
    WebServer ws = factory.getServer();
    ws.start();
    app = factory.createApp("/app");
    FlakLogin fl = new FlakLogin(app);
    app.servePath("/static", "/test-resources");
    fl.getSessionManager().setRequireLoggedInByDefault(true);
    fl.getSessionManager().setLoginPage("/static/login.html");
    app.scan(this);
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/test"));
  }

  @Test
  public void testServePathWithProtectedAccess() throws Exception {
    app = createApp();
    app.servePath("/static", "/test-resources", null, true);
    sessionManager.setLoginPage("/static/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  @Test
  public void testServeRootWithProtectedAccess() throws Exception {
    app = createApp();
    app.servePath("/", "/test-resources/", null, true);
    sessionManager.setLoginPage("/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  @Test
  public void testServeRootWithProtectedAccessAndClassLoader() throws Exception {
    app = createApp();
    app.servePath("/", "/test-resources/", getClass().getClassLoader(), true);
    sessionManager.setLoginPage("/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  private App createApp() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    AppFactory factory = Flak.getFactory();
    factory.setPort(9191);
    App app = factory.createApp();
    sessionManager = new FlakLogin(app).getSessionManager();
    return app;
  }

}
