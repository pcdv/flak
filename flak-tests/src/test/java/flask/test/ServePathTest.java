package flask.test;

import flak.App;
import flak.AppFactory;
import flak.ResourceOptions;
import flak.WebServer;
import flak.annotations.Route;
import flak.login.FlakLogin;
import flak.login.SessionManager;
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
    app.serveClasspath("/static", "/test-resources");
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
    app.serveClasspath("/static", "/test-resources");
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
    app.serveClasspath("/static", "/test-resources", new ResourceOptions().restricted());
    sessionManager.setLoginPage("/static/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  @Test
  public void testServeRootWithProtectedAccess() throws Exception {
    app = createApp();
    app.serveClasspath("/", "/test-resources/", new ResourceOptions().restricted());
    sessionManager.setLoginPage("/login.html");
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    Assert.assertEquals("Please login", client.get("/static/anything"));
  }

  /**
   * Without flak-login, nothing would keep anyone away from them.
   */
  @Test
  public void testRestrictedRequiresLogin() {
    AppFactory factory = TestUtil.getFactory();
    factory.setPlugins();
    app = factory.createApp();
    TestUtil.assertFails(() -> app.serveClasspath("/static", "/test-resources", new ResourceOptions().restricted()),
                         "Restricted resources require a plugin checking logins");
  }

  @Test
  public void testServeRootWithProtectedAccessAndClassLoader() throws Exception {
    app = createApp();
    app.serveClasspath("/",
                       "/test-resources/",
                       new ResourceOptions().restricted().classLoader(getClass().getClassLoader()));
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
