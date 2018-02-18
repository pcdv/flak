package flask.test;

import flak.Form;
import flak.Request;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.FlakSession;
import flak.login.FlakUser;
import flak.login.LoginPage;
import flak.login.LoginRequired;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests {@link SessionManager#getCurrentSession(Request)} method behavior.
 *
 * @author galvarez
 */
public class GetCurrentLoginTest extends AbstractAppTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void preScan() {
    installFlakLogin();
    sessionManager = flakLogin.getSessionManager();
  }

  @LoginPage
  @Route("/login")
  public String loginPage() {
    assertNull(getSession());
    return "Please login";
  }

  @Route("/logout")
  public void logout() {
    assertNotNull(getSession());
    sessionManager.closeCurrentSession(app.getRequest());
    assertNull(getSession());
    app.getResponse().redirect("/login");
  }

  private FlakSession getSession() {
    return sessionManager.getCurrentSession(app.getRequest());
  }

  @Route("/app")
  @LoginRequired
  public String appPage() {
    assertNotNull(getSession());
    return "Welcome";
  }

  @Post
  @Route(value = "/login")
  public void login(Form form) {
    String login = form.get("login");
    String pass = form.get("password");

    assertNull(getSession());

    if (login.equals("foo") && pass.equals("bar")) {
      FlakUser user = sessionManager.createUser(login);
      sessionManager.openSession(app, user, app.getResponse());
      // unintuitive but the login request does not contain the cookie
      assertNull(getSession());
      app.getResponse().redirect("/app");
    }
    else
      app.getResponse().redirect("/login");
  }

  @Test
  public void testLogin() throws Exception {
    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));

    // wrong login/password redirects to login page
    Assert.assertEquals("Please login",
                        client.post("/login", "login=foo&password="));

    // good login/password redirects to app
    Assert.assertEquals("Welcome",
                        client.post("/login", "login=foo&password=bar"));

    // app remains accessible thanks to session cookie
    Assert.assertEquals("Welcome", client.get("/app"));

    // logout link redirects to login page
    Assert.assertEquals("Please login", client.get("/logout"));

    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));
  }
}
