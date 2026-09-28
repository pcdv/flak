package flask.test;

import flak.App;
import flak.AppFactory;
import flak.Response;
import flak.annotations.Route;
import flak.login.DefaultFlakSession;
import flak.login.DefaultSessionManager;
import flak.login.DefaultUser;
import flak.login.FlakLogin;
import flak.login.FlakSession;
import org.junit.After;
import org.junit.Test;

import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the session cookie is marked Secure when the app is served over
 * HTTPS, and only then.
 */
public class SecureCookieTest {

  private App app;

  @After
  public void tearDown() {
    if (app != null)
      app.stop();
  }

  @Test
  public void testSecureOverHttps() throws Exception {
    SSLTest.trustAllCertificates();
    String cookie = login(true);
    assertTrue(cookie, cookie.contains("; Secure"));
  }

  @Test
  public void testNotSecureOverHttp() throws Exception {
    String cookie = login(false);
    assertFalse(cookie, cookie.contains("Secure"));
  }

  /**
   * A subclass marking the cookie Secure itself, e.g. behind a proxy
   * terminating TLS, does not get the attribute twice when the app is served
   * over HTTPS after all.
   */
  @Test
  public void testSecureAddedOnceOverHttps() throws Exception {
    SSLTest.trustAllCertificates();
    String cookie = login(true, new DefaultSessionManager() {
      @Override
      protected String generateSetCookieHeader(String path, FlakSession session) {
        return super.generateSetCookieHeader(path, session) + "; secure";
      }
    });
    assertEquals(cookie, 1, cookie.split("(?i)secure", -1).length - 1);
  }

  private String login(boolean https) throws Exception {
    return login(https, new DefaultSessionManager());
  }

  private String login(boolean https, DefaultSessionManager sessions) throws Exception {
    AppFactory factory = TestUtil.getFactory();
    if (https)
      factory.getServer().setSSLContext(SSLTest.getSslContext("/test-resources/lig.keystore", "foobar"));
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    app = factory.createApp();

    app.getPlugin(FlakLogin.class).setSessionManager(sessions);
    app.scan(new Object() {
      @Route("/login")
      public void login(Response r) {
        sessions.openSession(app,
                             new DefaultFlakSession(new DefaultUser("joe"),
                                                    sessions.generateSessionToken()),
                             r);
      }
    });
    app.start();

    // with a cookie handler installed, e.g. by SimpleClient, HttpURLConnection
    // hides HttpOnly cookies from getHeaderField(), as a browser would
    CookieHandler.setDefault(null);
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + "/login").openConnection();
    con.getResponseCode();
    return con.getHeaderField("Set-Cookie");
  }
}
