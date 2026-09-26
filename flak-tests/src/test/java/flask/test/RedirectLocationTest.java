package flask.test;

import flak.App;
import flak.AppFactory;
import flak.Query;
import flak.Response;
import flak.annotations.Route;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * Checks the Location of a redirect, for an app hosted at a path: a path is
 * relative to the app, a URL is kept as is.
 */
public class RedirectLocationTest {

  private App app;

  @Route("/redirect")
  public void redirect(Query q, Response r) {
    r.redirect(q.get("to"));
  }

  @Before
  public void setUp() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    app = factory.createApp("/app");
    app.scan(this);
    app.start();
  }

  @After
  public void tearDown() {
    app.stop();
  }

  @Test
  public void testLocation() throws Exception {
    assertEquals("/app/foo", location("/foo"));
    assertEquals("/app/foo?x=1", location("/foo?x=1"));
    assertEquals("https://example.com/x", location("https://example.com/x"));
    assertEquals("http://example.com", location("http://example.com"));
    assertEquals("//cdn.example.com/x", location("//cdn.example.com/x"));
  }

  private String location(String to) throws Exception {
    URL url = new URL(app.getRootUrl() + "/redirect?to=" + URLEncoder.encode(to, StandardCharsets.UTF_8));
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setInstanceFollowRedirects(false);
    assertEquals(302, con.getResponseCode());
    return con.getHeaderField("Location");
  }
}
