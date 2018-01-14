package net.jflask.test;

import flak.App;
import flak.Flak;
import flak.AppFactory;
import net.jflask.test.util.SimpleClient;
import net.jflask.test.util.ThreadState;
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

  @Before
  public void setUp() throws Exception {
    AppFactory fac = Flak.getFactory();
    fac.setHttpPort(9191);
    app = fac.createApp();

    preScan();
    app.scan(this);

    preStart();
    app.start();

    client = new SimpleClient("localhost", fac.getHttpPort());
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
