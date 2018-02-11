package flask.test;

import flak.Response;
import flak.SessionManager;
import flak.annotations.LoginNotRequired;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test used to fail because Set-Cookie header did not include any path.
 *
 * @author pcdv
 */
public class LoginTest4 extends AbstractAppTest {

  @LoginNotRequired
  @Route(value = "/auth/login", method = "POST")
  public Response login() {
    app.getSessionManager().loginUser("foo");
    return app.redirect("/hello");
  }

  @Route("/hello")
  public String hello() {
    return "yo";
  }

  @Route("/login")
  public String loginPage() {
    return "Please login";
  }

  @Test
  public void testCookieWithInvalidPath() throws Exception {
    SessionManager sm = this.app.getSessionManager();
    sm.setRequireLoggedInByDefault(true);
    sm.setLoginPage("/login");
    sm.setSessionCookieName("sesame");

    Assert.assertEquals("Please login", client.get("/hello"));
    Assert.assertEquals("yo", client.post("/auth/login", ""));
    Assert.assertEquals("yo", client.get("/hello"));
  }
}
