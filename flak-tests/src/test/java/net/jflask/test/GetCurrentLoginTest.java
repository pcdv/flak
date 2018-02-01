package net.jflask.test;

import flak.Form;
import flak.annotations.LoginPage;
import flak.annotations.LoginRequired;
import flak.annotations.Route;
import flak.Response;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests {@link flak.App#getCurrentLogin()} method behavior.
 *
 * @author galvarez
 */
public class GetCurrentLoginTest extends AbstractAppTest {

  @LoginPage
  @Route("/login")
  public String loginPage() {
    assertNull(app.getCurrentLogin());
    return "Please login";
  }

  @Route("/logout")
  public Response logout() {
    assertNotNull(app.getCurrentLogin());
    app.logoutUser();
    assertNull(app.getCurrentLogin());
    return app.redirect("/login");
  }

  @Route("/app")
  @LoginRequired
  public String appPage() {
    assertNotNull(app.getCurrentLogin());
    return "Welcome";
  }

  @Route(value = "/login", method = "POST")
  public Response login(Form form) {
    String login = form.get("login");
    String pass = form.get("password");

    assertNull(app.getCurrentLogin());

    if (login.equals("foo") && pass.equals("bar")) {
      app.loginUser(login);
      // unintuitive but the login request does not contain the cookie
      assertNull(app.getCurrentLogin());
      return app.redirect("/app");
    }

    return app.redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {
    // app redirects to login page when not logged in
    assertEquals("Please login", client.get("/app"));

    // wrong login/password redirects to login page
    assertEquals("Please login", client.post("/login", "login=foo&password="));

    // good login/password redirects to app
    assertEquals("Welcome", client.post("/login", "login=foo&password=bar"));

    // app remains accessible thanks to session cookie
    assertEquals("Welcome", client.get("/app"));

    // logout link redirects to login page
    assertEquals("Please login", client.get("/logout"));

    // app redirects to login page when not logged in
    assertEquals("Please login", client.get("/app"));
  }
}
