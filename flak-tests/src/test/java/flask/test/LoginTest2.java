package flask.test;

import flak.Form;
import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.LoginNotRequired;
import flak.login.LoginPage;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * Variant of LoginTest that uses @LoginNotRequired and
 * {@link SessionManager#setRequireLoggedInByDefault(boolean)}.
 *
 * @author pcdv
 */
public class LoginTest2 extends AbstractAppTest {

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
  public String appPage(SessionManager sessionManager) {
    return "Welcome " + sessionManager.getCurrentSession(app.getRequest())
                                      .getUser()
                                      .getId();
  }

  @Route(value = "/login")
  @Post
  @LoginNotRequired
  public void login(Response r, Form form, SessionManager sessionManager) {
    String login = form.get("login");
    String pass = form.get("password");

    if (login.equals("foo") && pass.equals("bar")) {
      DefaultSessionManager dsm = (DefaultSessionManager) sessionManager;
      sessionManager.openSession(app, dsm.createUser("foo"), r);
      r.redirect("/app");
    }
    r.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {

    flakLogin.getSessionManager().setRequireLoggedInByDefault(true);

    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));

    // wrong login/password redirects to login page
    Assert.assertEquals("Please login",
                        client.post("/login", "login=foo&password="));

    // good login/password redirects to app
    Assert.assertEquals("Welcome foo",
                        client.post("/login", "login=foo&password=bar"));

    // app remains accessible thanks to session cookie
    Assert.assertEquals("Welcome foo", client.get("/app"));

    // logout link redirects to login page
    Assert.assertEquals("Please login", client.get("/logout"));

    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));
  }
}
