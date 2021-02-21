package flask.test;

import flak.App;
import flak.AppFactory;
import flak.login.FlakLogin;
import flak.login.SessionManager;
import flask.test.util.DebugProxy;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;

public class AbstractAppTest {

  protected App app;

  protected SimpleClient client;

  /**
   * Checks that the test did not leave behind any running thread.
   */
  @Rule
  public ThreadState.ThreadStateRule noZombies =
      new ThreadState.ThreadStateRule();

  protected FlakLogin flakLogin;

  protected SessionManager sessionManager;
  protected DebugProxy proxy;

  private static boolean USE_PROXY = Boolean.getBoolean("useDebugProxy");

  @Before
  public void setUp() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setPort(9191);
    app = factory.createApp();

    preScan();
    app.scan(this);

    preStart();
    app.start();

    if (USE_PROXY) {
      proxy = new DebugProxy(9092, "localhost", 9191);
      client = new SimpleClient(app.getRootUrl().replace("9191", "9092"));
    }
    else {
      client = new SimpleClient(app.getRootUrl());
    }
  }

  protected void initFlakLogin() {
    this.flakLogin = app.getPlugin(FlakLogin.class);
    this.sessionManager = flakLogin.getSessionManager();
  }

  /**
   * Override this method to execute code before the app scans request
   * handlers.
   */
  protected void preScan() {
  }

  /**
   * Override this method to execute code before the app is started.
   */
  protected void preStart() {
  }

  @After
  public void tearDown() throws IOException {
    if (app != null)
      app.stop();
    if (proxy != null)
      proxy.close();
  }
}
