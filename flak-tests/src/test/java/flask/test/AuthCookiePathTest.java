package flask.test;

import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.FlakUser;
import flak.login.LoginNotRequired;
import org.junit.Assert;
import org.junit.Test;

/**
 * Reproduce invalid cookie path generated when app path is null|""|/
 */
public class AuthCookiePathTest extends AbstractAppTest {

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

  @Route("/")
  public String loginPage() {
    return "Please login";
  }

  @LoginNotRequired
  @Route("/api/login")
  @Post
  public void login(Response r) {
    FlakUser user = sessionManager.createUser("test");
    sessionManager.openSession(app, user, r);
  }

  @Test
  public void testLoginLogout() throws Exception {
    Assert.assertEquals("Please login", client.get("/data"));
    client.post("/api/login", "");
    Assert.assertEquals("OK", client.get("/data"));
    Assert.assertEquals("/", client.getCookie(sessionManager.getAuthTokenCookieName()).getPath());
  }
}
