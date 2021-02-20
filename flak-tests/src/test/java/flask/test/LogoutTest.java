package flask.test;

import java.io.IOException;

import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.FlakUser;
import flak.login.LoginNotRequired;
import org.junit.Assert;
import org.junit.Test;

/**
 * Reproduce bug when login page = "/"
 */
public class LogoutTest extends AbstractAppTest {

  @Override
  protected void preScan() {
    initFlakLogin();
    sessionManager.setLoginPage("/");
    sessionManager.setRequireLoggedInByDefault(true);
  }

  @Route("/data")
  public String getData() {
    return "OK";
  }

  @Route("/")
  public String loginPage() {
    return "Please login";
  }

  @LoginNotRequired
  @Route("/login")
  @Post
  public void login(Response r) {
    FlakUser user = sessionManager.createUser("test");
    sessionManager.openSession(app, user, r);
  }

  @Test
  public void testLoginLogout() throws IOException {
    Assert.assertEquals("Please login", client.get("/data"));
    client.post("/login", "");
    Assert.assertEquals("OK", client.get("/data"));
  }
}
