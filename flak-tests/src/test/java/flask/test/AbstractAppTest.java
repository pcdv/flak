package flask.test;

import flak.App;
import flak.AppFactory;
import flak.login.FlakLogin;
import flak.login.SessionManager;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

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

  @Before
  public void setUp() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setPort(9191);
    app = factory.createApp();

    preScan();
    app.scan(this);

    preStart();
    app.start();

    client = new SimpleClient(app.getRootUrl());
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
  public void tearDown() {
    if (app != null)
      app.stop();
  }

}
