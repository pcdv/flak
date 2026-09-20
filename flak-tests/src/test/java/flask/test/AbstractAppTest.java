package flask.test;

import flak.App;
import flak.AppFactory;
import flak.login.DefaultSessionManager;
import flak.login.FlakLogin;
import flask.test.util.DebugProxy;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class AbstractAppTest {

  protected App app;

  protected SimpleClient client;

  /**
   * Checks that the test did not leave behind any running thread.
   * <p>
   * netty starts a housekeeping thread of its own on the first request and
   * lets it die after about a second of inactivity. Waiting for it would add
   * that second to every single test, and it is not ours to clean up.
   */
  @Rule
  public ThreadState.ThreadStateRule noZombies =
      new ThreadState.ThreadStateRule("globalEventExecutor-.*");

  protected FlakLogin flakLogin;

  protected DefaultSessionManager sessionManager;
  protected DebugProxy proxy;

  private static boolean USE_PROXY = Boolean.getBoolean("useDebugProxy");

  @Before
  public void setUp() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    app = factory.createApp();

    preScan();
    app.scan(this);

    preStart();
    app.start();

    if (USE_PROXY) {
      final int port = app.getServer().getPort();
      proxy = new DebugProxy(0, "localhost", port);
      client = new SimpleClient(app.getRootUrl()
                                .replace(String.valueOf(port),
                                         String.valueOf(proxy.getPort())));
    }
    else {
      client = new SimpleClient(app.getRootUrl());
    }
    client.addHeader("Accept-Encoding", "gzip");
  }

  protected void initFlakLogin() {
    this.flakLogin = app.getPlugin(FlakLogin.class);
    this.sessionManager = (DefaultSessionManager) flakLogin.getSessionManager();
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
