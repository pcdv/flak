package flask.test;

import flak.Form;
import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.LoginNotRequired;
import flak.login.LoginPage;
import flak.login.LoginRequired;
import flak.login.SessionManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Reproduces bug when old cookies are set in browser.
 *
 * @author pcdv
 */
public class LoginTest3 extends AbstractAppTest {

  @Override
  protected void preScan() {
    installFlakLogin();
  }

  @LoginPage
  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Route("/logout")
  public void logout(SessionManager sessionManager) {
    sessionManager.closeCurrentSession(app.getRequest());
    app.getResponse().redirect("/login");
  }

  @Route("/app")
  @LoginRequired
  public String appPage() {
    return "Welcome";
  }

  @Post
  @LoginNotRequired
  @Route(value = "/login")
  public void login(Response r, Form form, SessionManager sessionManager) {
    String login = form.get("login");
    String pass = form.get("password");

    if (login.equals("foo") && pass.equals("bar")) {
      DefaultSessionManager dsm = (DefaultSessionManager) sessionManager;
      sessionManager.openSession(app, dsm.createUser(login), r);
      r.redirect("/app");
    }
    else
      r.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {

    // this is not needed but without it the test sometimes fails due to a
    // probable bug in HttpUrlConnection ...
    client.get("/app");

    // good login/password redirects to app
    assertEquals("Welcome", client.post("/login", "login=foo&password=bar"));

    // NB: after a couple iterations, the above generally fails with a
    // "400 Bad Request" error. Looks like HttpUrlConnection sometimes sends
    // an invalid HTTP request without the leading GET => see workaround at
    // beginning of test

    // app remains accessible thanks to session cookie
    assertEquals("Welcome", client.get("/app"));

  }
}
