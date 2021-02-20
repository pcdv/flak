package flask.test;

import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.LoginNotRequired;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test used to fail because Set-Cookie header did not include any path.
 *
 * @author pcdv
 */
public class LoginTest4 extends AbstractAppTest {
  @Override
  protected void preScan() {
    initFlakLogin();
  }

  @LoginNotRequired
  @Post
  @Route(value = "/auth/login")
  public void login(SessionManager dsm) {
    dsm.openSession(app, dsm.createUser("foo"), app.getResponse());
    app.getResponse().redirect("/hello");
  }

  @Route("/hello")
  public String hello() {
    return "yo";
  }

  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Test
  public void testCookieWithInvalidPath() throws Exception {
    SessionManager sm = flakLogin.getSessionManager();
    sm.setRequireLoggedInByDefault(true);
    sm.setLoginPage("/login");
    sm.setAuthTokenCookieName("sesame");

    Assert.assertEquals("Please login", client.get("/hello"));
    Assert.assertEquals("yo", client.post("/auth/login", ""));
    Assert.assertEquals("yo", client.get("/hello"));
  }
}
