package flask.test;

import flak.annotations.Post;
import flak.annotations.Route;
import flak.util.Cors;
import flak.util.CrossOriginProtection;
import flask.test.util.RawHttp;
import flask.test.util.RawHttp.Reply;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Requests as browsers send them, with headers that HttpURLConnection refuses
 * to send.
 */
public class CrossOriginProtectionTest extends AbstractAppTest {

  private static final String UI = "https://ui.example.com";

  private final CrossOriginProtection protection = new CrossOriginProtection();

  private final AtomicInteger posts = new AtomicInteger();

  @Override
  protected void preScan() {
    app.addBeforeAllHook(new Cors().allowOrigins(UI).allowCredentials(true));
    app.addBeforeAllHook(protection);
  }

  @Route("/api/items")
  public String items() {
    return "items";
  }

  @Route("/api/items")
  @Post
  public String create() {
    posts.incrementAndGet();
    return "posted";
  }

  @Test
  public void testSameOrigin() throws IOException {
    assertPosted(post("Sec-Fetch-Site: same-origin", "Origin: http://localhost"));
  }

  /**
   * The user typed the URL, or opened a bookmark.
   */
  @Test
  public void testUserInitiated() throws IOException {
    assertPosted(post("Sec-Fetch-Site: none"));
  }

  @Test
  public void testCrossSite() throws IOException {
    Reply r = post("Sec-Fetch-Site: cross-site", "Origin: https://evil.example.org");
    assertEquals(403, r.status());
    assertEquals("Cross-origin request refused", r.body());
    assertEquals(0, posts.get());
  }

  /**
   * Another host of the same domain: the cookies of the app go with its
   * requests, SameSite=Strict ones included.
   */
  @Test
  public void testSameSite() throws IOException {
    assertEquals(403, post("Sec-Fetch-Site: same-site", "Origin: https://evil.example.com").status());
    assertEquals(0, posts.get());
  }

  /**
   * Browsers never send the form of another site without an Origin header.
   */
  @Test
  public void testFormOfAnotherSite() throws IOException {
    Reply r = post("Sec-Fetch-Site: cross-site",
                   "Origin: https://evil.example.org",
                   "Content-Type: application/x-www-form-urlencoded");
    assertEquals(403, r.status());
    assertEquals(0, posts.get());
  }

  @Test
  public void testSafeMethod() throws IOException {
    Reply r = send("GET /api/items", "Sec-Fetch-Site: cross-site", "Origin: https://evil.example.org");
    assertEquals(200, r.status());
    assertEquals("items", r.body());
  }

  @Test
  public void testTrustedOrigin() throws IOException {
    protection.trustOrigins(UI);

    // the preflight request, answered by Cors
    Reply pre = send("OPTIONS /api/items",
                     "Sec-Fetch-Site: same-site",
                     "Origin: " + UI,
                     "Access-Control-Request-Method: POST");
    assertEquals(204, pre.status());

    Reply r = post("Sec-Fetch-Site: same-site", "Origin: " + UI);
    assertPosted(r);
    assertEquals(UI, r.header("Access-Control-Allow-Origin"));
  }

  /**
   * curl, HTTP libraries, scripts: not browsers acting for a user.
   */
  @Test
  public void testNotABrowser() throws IOException {
    assertPosted(post());
  }

  /**
   * Browsers that do not send Sec-Fetch-Site (before 2023).
   */
  @Test
  public void testOldBrowser() throws IOException {
    assertPosted(post("Origin: http://localhost"));
    assertPosted(post("Host: localhost:8080", "Origin: http://LOCALHOST:8080"));

    assertEquals(403, post("Origin: https://evil.example.org").status());
    assertEquals(403, post("Host: localhost:8080", "Origin: http://localhost:9090").status());
    // a sandboxed iframe, or a file
    assertEquals(403, post("Origin: null").status());
    assertEquals(2, posts.get());
  }

  private void assertPosted(Reply r) {
    assertEquals(200, r.status());
    assertEquals("posted", r.body());
  }

  private Reply post(String... headers) throws IOException {
    String[] all = new String[headers.length + 1];
    System.arraycopy(headers, 0, all, 0, headers.length);
    all[headers.length] = "Content-Length: 0";
    return send("POST /api/items", all);
  }

  private Reply send(String requestLine, String... headers) throws IOException {
    return RawHttp.send(app.getRootUrl(), requestLine, headers);
  }
}
