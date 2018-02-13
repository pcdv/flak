package flask.test;

import flak.Form;
import flak.Response;
import flak.login.LoginNotRequired;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * Try to reproduce bug where failed login does not prevent user from
 * accessing restricted pages. Turns out the browser (chrome) had several
 * duplicates of the session cookie and deleting one on logout was not enough
 * to disable the session (should be fixed now that cookie is set with a path).
 *
 * @author pcdv
 */
public class LoginTest5 extends AbstractAppTest {

  private SessionManager sm;

  @Override
  protected void preScan() {
    installFlakLogin();
    sm = flakLogin.getSessionManager();
  }

  @Post
  @LoginNotRequired
  @Route(value = "/auth/login")
  public void login(Response r, Form form) {
    String login = form.get("login");
    String pass = form.get("password");

    if (login.equals("foo") && pass.equals("bar")) {
      sm.loginUser(login);
      app.redirect("/hello");
    }
    else
      sm.redirectToLogin(r);
  }

  @Route("/hello")
  public String hello() {
    return "yo";
  }

  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Route("/auth/logout")
  public void logout(Response response) {
    sm.logoutUser();
    sm.redirectToLogin(response);
  }

  @Test
  public void testCookieWithInvalidPath() throws Exception {
    sm.setRequireLoggedInByDefault(true);
    sm.setLoginPage("/login");

    Assert.assertEquals("Please login", client.get("/hello"));
    Assert.assertEquals("yo",
                        client.post("/auth/login", "login=foo&password=bar"));
    Assert.assertEquals("yo", client.get("/hello"));
    Assert.assertEquals("Please login", client.get("/auth/logout"));
    Assert.assertEquals("Please login", client.get("/hello"));
  }
}
