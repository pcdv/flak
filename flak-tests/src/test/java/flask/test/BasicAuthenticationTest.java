package flask.test;

import flak.Request;
import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.DefaultSessionManager;
import flak.login.DefaultUser;
import flak.login.FlakLogin;
import flak.login.FlakUser;
import flak.login.LoginNotRequired;
import flak.login.LoginPage;
import flak.login.LoginRequired;
import flak.login.SessionManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;

public class BasicAuthenticationTest extends AbstractAppTest {

  @LoginPage
  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Route("/app")
  @LoginRequired
  public String appPage() {
    return "Welcome";
  }

  @Post
  @Route("/login")
  @LoginNotRequired
  public void login(Request re, Response resp, SessionManager sessionManager) {
    String auth = re.getHeader("Authorization");
    if (auth != null && auth.startsWith("Basic ")) {
      String s = new String(Base64.getDecoder().decode(auth.substring(6)));
      int pos = s.indexOf(':');
      if (pos != -1) {
        String login = s.substring(0, pos);
        String pass = s.substring(pos + 1);

        if (login.equals("foo") && pass.equals("bar")) {
          DefaultSessionManager dsm = (DefaultSessionManager) sessionManager;
          FlakUser user = dsm.createUser(login);
          dsm.openSession(app, user, resp);
          resp.redirect("/app");
          return;
        }
      }
    }
    resp.redirect("/login");
  }

  @Override
  protected void initFlakLogin() {
    this.flakLogin = app.getPlugin(FlakLogin.class);
    sessionManager = new BasicAuthSessionManager();
    sessionManager.addUser(new DefaultUser("foo"));
    flakLogin.setSessionManager(sessionManager);
  }

  @Override
  protected void preScan() {
    initFlakLogin();
  }

  @Test
  public void testLogin() throws Exception {
    // app redirects to login page when not logged in
    Assert.assertEquals("Please login", client.get("/app"));

    // wrong login/password redirects to login page
    client.authBasic("foo", "wrong");
    Assert.assertEquals("Please login", client.get("/app"));

    // good login/password redirects to app
    client.authBasic("foo", "secret");
    Assert.assertEquals("Welcome", client.get("/app"));
  }
}
