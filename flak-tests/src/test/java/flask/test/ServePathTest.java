package flask.test;

import flak.App;
import flak.AppFactory;
import flak.WebServer;
import flak.annotations.Route;
import flak.login.FlakLogin;
import flak.login.SessionManager;
import flak.plugin.resource.FlakResourceImpl;
import flask.test.util.SimpleClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ServePathTest {

  private App app;

  private SessionManager sessionManager;

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
    AppFactory fac = TestUtil.getFactory();
    fac.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    WebServer ws = fac.getServer();
    ws.start();
    app = fac.createApp("/app");
    new FlakResourceImpl(app).servePath("/static", "/test-resources");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("FOO", client.get("/static/foo.html"));
  }

  @Test
  public void testRedirectLoginToResource() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    WebServer ws = factory.getServer();
    ws.start();
    app = factory.createApp("/app");
    FlakLogin fl = app.getPlugin(FlakLogin.class);
    new FlakResourceImpl(app).servePath("/static", "/test-resources");
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
    new FlakResourceImpl(app).servePath("/static",
                                        "/test-resources",
                                        null,
                                        true);
    sessionManager.setLoginPage("/static/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  @Test
  public void testServeRootWithProtectedAccess() throws Exception {
    app = createApp();
    new FlakResourceImpl(app).servePath("/", "/test-resources/", null, true);
    sessionManager.setLoginPage("/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  @Test
  public void testServeRootWithProtectedAccessAndClassLoader() throws Exception {
    app = createApp();
    new FlakResourceImpl(app).servePath("/",
                                        "/test-resources/",
                                        getClass().getClassLoader(),
                                        true);
    sessionManager.setLoginPage("/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  private App createApp() {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    App app = factory.createApp();
    sessionManager = app.getPlugin(FlakLogin.class).getSessionManager();
    return app;
  }

}
