package flask.test;

import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.DefaultFlakSession;
import flak.login.FlakSession;
import flak.login.FlakUser;
import flak.login.LoginNotRequired;
import org.junit.Assert;
import org.junit.Test;

import java.net.HttpCookie;

/**
 * Tests expiration of cookies.
 */
public class AuthCookieExpiryTest extends AbstractAppTest {

  private long maxAgeMillis = 10_000L;

  @Override
  protected void preScan() {
    initFlakLogin();
    sessionManager.setLoginPage("/");
    sessionManager.setRequireLoggedInByDefault(true);
  }

  @Route("/data")
  public String getData() {
    return "OK";
  }

  @LoginNotRequired
  @Route("/")
  public String loginPage() {
    return "Please login";
  }

  @LoginNotRequired
  @Route("/api/login")
  @Post
  public void login(Response r) {
    FlakUser user = sessionManager.createUser("test");
    long expiry = System.currentTimeMillis() + maxAgeMillis;
    FlakSession session = new DefaultFlakSession(user, sessionManager.generateSessionToken(), expiry);
    sessionManager.setCookie(app, session, r);
    sessionManager.addSession(session);
  }

  @Test
  public void testLoginLogout() throws Exception {
    Assert.assertEquals("Please login", client.get("/data"));
    client.post("/api/login", "");
    HttpCookie cookie = client.getCookie(sessionManager.getAuthTokenCookieName());
    Assert.assertTrue(cookie.getMaxAge() == 9 || cookie.getMaxAge() == 10);
    Assert.assertEquals("OK", client.get("/data"));
    Assert.assertEquals("test", sessionManager.getSessionForToken(cookie.getValue()).getUser().getId());
  }

  @Test
  public void testSessionIsInvalidated() throws Exception {
    maxAgeMillis = 5000;
    client.post("/api/login", "");
    Assert.assertSame(1, sessionManager.getSessionCount());
    sessionManager.setTimeProvider(() -> System.currentTimeMillis() + 10000);
    client.get("/data");
    Assert.assertSame(0, sessionManager.getSessionCount());
  }
}
