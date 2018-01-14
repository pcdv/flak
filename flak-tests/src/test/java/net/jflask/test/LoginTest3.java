package net.jflask.test;

import flak.Response;
import flak.annotations.LoginPage;
import flak.annotations.LoginRequired;
import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Reproduces bug when old cookies are set in browser.
 *
 * @author pcdv
 */
public class LoginTest3 extends AbstractAppTest {

  @LoginPage
  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Route("/logout")
  public Response logout() {
    app.logoutUser();
    return app.redirect("/login");
  }

  @Route("/app")
  @LoginRequired
  public String appPage() {
    return "Welcome";
  }

  @Route(value = "/login", method = "POST")
  public Response login() {
    String login = app.getRequest().getForm("login");
    String pass = app.getRequest().getForm("password");

    if (login.equals("foo") && pass.equals("bar")) {
      app.loginUser(login);
      return app.redirect("/app");
    }

    return app.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {

    // good login/password redirects to app
    assertEquals("Welcome", client.post("/login", "login=foo&password=bar"));

    // app remains accessible thanks to session cookie
    assertEquals("Welcome", client.get("/app"));

  }
}
