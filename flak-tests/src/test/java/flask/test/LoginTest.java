package flask.test;

import flak.Form;
import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.FlakSession;
import flak.login.FlakUser;
import flak.login.LoginPage;
import flak.login.LoginRequired;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

import java.net.HttpCookie;

/**
 * Tests basic authentication mechanisms.
 *
 * @author pcdv
 */
public class LoginTest extends AbstractAppTest {
  @Override
  protected void preScan() {
    initFlakLogin();
  }

  @LoginPage
  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Route("/logout")
  public void logout(SessionManager sessionManager) {
    FlakSession session = sessionManager.getCurrentSession(app.getRequest());
    sessionManager.closeSession(session);
    app.getResponse().redirect("/login");
  }

  @Route("/app")
  @LoginRequired
  public String appPage(FlakUser user) {
    return "Welcome " + user.getId();
  }

  @Post
  @Route(value = "/login")
  public void login(Response r, Form form, SessionManager sessionManager) {
    String login = form.get("login");
    String pass = form.get("password");

    if (login.equals("foo") && pass.equals("bar")) {
      SessionManager dsm = sessionManager;
      FlakUser user = dsm.createUser(login);
      dsm.openSession(app, user, r);
      r.redirect("/app");
    }
    else
      r.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {
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

    HttpCookie cookie = client.getCookie("sessionToken");
    Assert.assertTrue(cookie.isHttpOnly());

    // logout link redirects to login page
    Assert.assertEquals("Please login", client.get("/logout"));

    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));
  }
}
