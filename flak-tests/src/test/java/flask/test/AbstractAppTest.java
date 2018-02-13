package flask.test;

import flak.App;
import flak.Flak;
import flak.login.FlakLogin;
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

  @Before
  public void setUp() throws Exception {
    app = Flak.createHttpApp(9191);

    preScan();
    app.scan(this);

    preStart();
    app.start();

    client = new SimpleClient(app.getRootUrl());
  }

  protected void installFlakLogin() {
    flakLogin = new FlakLogin(app);
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
    app.stop();
  }

}
