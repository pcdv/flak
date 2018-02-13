package flask.test;

import flak.Form;
import flak.Response;
import flak.login.LoginPage;
import flak.login.LoginRequired;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests basic authentication mechanisms.
 *
 * @author pcdv
 */
public class LoginTest extends AbstractAppTest {
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
  public Response logout(SessionManager sessionManager) {
    sessionManager.logoutUser();
    return app.redirect("/login");
  }

  @Route("/app")
  @LoginRequired
  public String appPage() {
    return "Welcome";
  }

  @Post
  @Route(value = "/login")
  public Response login(Form form, SessionManager sessionManager) {
    String login = form.get("login");
    String pass = form.get("password");

    if (login.equals("foo") && pass.equals("bar")) {
      sessionManager.loginUser(login);
      return app.redirect("/app");
    }

    return app.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {
    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));

    // wrong login/password redirects to login page
    Assert.assertEquals("Please login", client.post("/login", "login=foo&password="));

    // good login/password redirects to app
    Assert.assertEquals("Welcome", client.post("/login", "login=foo&password=bar"));

    // app remains accessible thanks to session cookie
    Assert.assertEquals("Welcome", client.get("/app"));

    // logout link redirects to login page
    Assert.assertEquals("Please login", client.get("/logout"));

    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));
  }
}
