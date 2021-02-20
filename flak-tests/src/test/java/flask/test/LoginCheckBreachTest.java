package flask.test;

import flak.AppFactory;
import flak.Flak;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.FlakLogin;
import flak.spi.AbstractApp;
import flask.test.util.SimpleClient;
import org.junit.Test;

import java.io.IOException;

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
    AppFactory factory = Flak.getFactory();
    factory.setPort(9191);
    // prevent automatic addition of FlakLogin plugin
    factory.setPluginValidator(cls -> false);
    app = factory.createApp();
    client = new SimpleClient(app.getRootUrl());
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

    ((AbstractApp) app).addPlugin(new FlakLogin(app));
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

    TestUtil.assertFails(() -> client.get("/test2"), "403");
    TestUtil.assertFails(() -> client.get("/test1"), "403");
  }
}
