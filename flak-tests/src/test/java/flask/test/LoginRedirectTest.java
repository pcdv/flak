package flask.test;

import flak.annotations.QueryParam;
import flak.annotations.Route;
import flak.login.LoginRequired;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;

/**
 * Checks that a user redirected to the login page is told where to go back,
 * query string included.
 */
public class LoginRedirectTest extends AbstractAppTest {

  @Override
  protected void preScan() {
    initFlakLogin();
    sessionManager.setLoginPage("/login");
  }

  @Route("/login")
  public String login(@QueryParam("url") String url) {
    return "url=" + url;
  }

  @Route("/login2")
  public String login2(@QueryParam("lang") String lang, @QueryParam("url") String url) {
    return "lang=" + lang + " url=" + url;
  }

  @LoginRequired
  @Route("/data")
  public String data() {
    return "DATA";
  }

  @Test
  public void testLocation() throws Exception {
    assertEquals("/login?url=%2Fdata", location("/data"));
    assertEquals("/login?url=%2Fdata%3Fx%3D1%26y%3Da%2526b", location("/data?x=1&y=a%26b"));
  }

  @Test
  public void testLoginPageReceivesUrl() throws Exception {
    assertEquals("url=/data", client.get("/data"));
    // exactly what was requested, to be redirected to once logged in
    assertEquals("url=/data?x=1&y=a%26b", client.get("/data?x=1&y=a%26b"));
  }

  @Test
  public void testLoginPageWithQuery() throws Exception {
    sessionManager.setLoginPage("/login2?lang=fr");
    assertEquals("/login2?lang=fr&url=%2Fdata%3Fx%3D1", location("/data?x=1"));
    assertEquals("lang=fr url=/data?x=1", client.get("/data?x=1"));
  }

  private String location(String path) throws Exception {
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + path).openConnection();
    con.setInstanceFollowRedirects(false);
    assertEquals(302, con.getResponseCode());
    return con.getHeaderField("Location");
  }
}
