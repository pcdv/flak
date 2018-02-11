package flask.test;

import flak.Form;
import flak.Response;
import flak.annotations.LoginNotRequired;
import flak.annotations.LoginPage;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * Variant of LoginTest that uses @LoginNotRequired and
 * {@link flak.SessionManager#setRequireLoggedInByDefault(boolean)}.
 *
 * @author pcdv
 */
public class LoginTest2 extends AbstractAppTest {

  @LoginPage
  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Route("/logout")
  public Response logout() {
    app.getSessionManager().logoutUser();
    return app.redirect("/login");
  }

  @Route("/app")
  public String appPage() {
    return "Welcome " + app.getSessionManager().getCurrentLogin();
  }

  @Route(value = "/login", method = "POST")
  @LoginNotRequired
  public Response login(Form form) {
    String login = form.get("login");
    String pass = form.get("password");

    if (login.equals("foo") && pass.equals("bar")) {
      app.getSessionManager().loginUser(login);
      return app.redirect("/app");
    }

    return app.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {

    app.getSessionManager().setRequireLoggedInByDefault(true);

    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));

    // wrong login/password redirects to login page
    Assert.assertEquals("Please login", client.post("/login", "login=foo&password="));

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
