package flask.test;

import flak.AppFactory;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.FlakLogin;
import flask.test.util.DebugProxy;
import flask.test.util.SimpleClient;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Reproduce bug that cause URLs to not be protected by a login check
 * in some conditions.
 *
 * The bug happened if the session manager was replaced after some
 * method handlers were registered.
 */
public class LoginCheckBreachTest extends AbstractAppTest {

  @Override
  public void setUp() throws Exception {
    // disable default setup to produce an init race between handlers and FlakLogin
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    // prevent automatic addition of FlakLogin plugin
    factory.setPluginValidator(cls -> false);
    factory.getServer().start();
    final int port = factory.getPort();
    app = factory.createApp();
    proxy = new DebugProxy(9092, "localhost", port);
    client = new SimpleClient(app.getRootUrl().replace(String.valueOf(port), "9092"));
  }

  @Test
  public void testIt() throws IOException {

    // scan one handler before FlakLogin is installed
    app.scan(new Object() {
      @Route("/test1")
      public String test() {
        return "OK";
      }
    });

    app.addPlugin(new FlakLogin(app));
    initFlakLogin();
    app.start();

    // change session manager and enable login check
    sessionManager = new DefaultSessionManager();
    sessionManager.setRequireLoggedInByDefault(true);
    FlakLogin fl = app.getPlugin(FlakLogin.class);
    fl.setSessionManager(sessionManager);

    // scan another method handler
    app.scan(new Object() {
      @Route("/test2")
      public String test() {
        return "OK";
      }
    });

    TestUtil.assertFails(() -> client.get("/test1"), "403");
    TestUtil.assertFails(() -> client.get("/test2"), "403");
  }
}
