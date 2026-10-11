package flask.test;

import flak.annotations.Put;
import flak.annotations.Route;
import flak.login.LoginRequired;
import flak.util.Cors;
import flask.test.util.RawHttp;
import flask.test.util.RawHttp.Reply;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Requests as browsers send them from a page of another origin. Sent over a
 * bare socket: HttpURLConnection refuses to send an Origin header.
 */
public class CorsTest extends AbstractAppTest {

  private static final String UI = "https://ui.example.com";

  private final Cors cors = new Cors();

  @Override
  protected void preScan() {
    initFlakLogin();
    app.addBeforeAllHook(cors);
  }

  @Route("/api/items")
  public String items() {
    return "items";
  }

  @Route("/api/items")
  @Put
  public String put() {
    return "put";
  }

  @Route("/api/private")
  @LoginRequired
  public String secret() {
    return "secret";
  }

  @Test
  public void testSimpleRequest() throws IOException {
    cors.allowOrigins(UI);
    Reply r = send("GET /api/items", "Origin: " + UI);
    assertEquals(200, r.status());
    assertEquals("items", r.body());
    assertEquals(UI, r.header("Access-Control-Allow-Origin"));
    assertTrue(r.headers("Vary").contains("Origin"));
    assertNull(r.header("Access-Control-Allow-Credentials"));
  }

  @Test
  public void testOtherOriginIsServedWithoutHeaders() throws IOException {
    cors.allowOrigins(UI);
    Reply r = send("GET /api/items", "Origin: https://evil.example.org");
    assertEquals(200, r.status());
    assertNull(r.header("Access-Control-Allow-Origin"));
  }

  @Test
  public void testNoOriginNoHeaders() throws IOException {
    cors.allowOrigins(UI);
    Reply r = send("GET /api/items");
    assertEquals("items", r.body());
    assertNull(r.header("Access-Control-Allow-Origin"));
    assertNull(r.header("Vary"));
  }

  @Test
  public void testPreflight() throws IOException {
    cors.allowOrigins(UI);
    Reply r = send("OPTIONS /api/items",
                   "Origin: " + UI,
                   "Access-Control-Request-Method: PUT",
                   "Access-Control-Request-Headers: content-type, x-request-id");
    assertEquals(204, r.status());
    assertEquals("", r.body());
    assertEquals(UI, r.header("Access-Control-Allow-Origin"));
    assertEquals("GET, HEAD, POST, PUT, PATCH, DELETE", r.header("Access-Control-Allow-Methods"));
    assertEquals("content-type, x-request-id", r.header("Access-Control-Allow-Headers"));
    assertEquals("600", r.header("Access-Control-Max-Age"));

    Reply put = send("PUT /api/items", "Origin: " + UI, "Content-Length: 0");
    assertEquals("put", put.body());
    assertEquals(UI, put.header("Access-Control-Allow-Origin"));
  }

  /**
   * Browsers send no credentials with a preflight request.
   */
  @Test
  public void testPreflightOfARestrictedRoute() throws IOException {
    cors.allowOrigins(UI);
    Reply r = send("OPTIONS /api/private",
                   "Origin: " + UI,
                   "Access-Control-Request-Method: GET");
    assertEquals(204, r.status());

    // and the request itself is rejected with the headers, so that the page
    // can read why
    Reply get = send("GET /api/private", "Origin: " + UI);
    assertEquals(401, get.status());
    assertEquals(UI, get.header("Access-Control-Allow-Origin"));
  }

  @Test
  public void testPreflightOfAnUnknownRoute() throws IOException {
    cors.allowOrigins(UI);
    Reply r = send("OPTIONS /nowhere", "Origin: " + UI, "Access-Control-Request-Method: GET");
    assertEquals(204, r.status());
    assertEquals(404, send("GET /nowhere", "Origin: " + UI).status());
  }

  @Test
  public void testPreflightRefused() throws IOException {
    cors.allowOrigins(UI).allowMethods("GET");
    assertEquals(403, send("OPTIONS /api/items",
                           "Origin: https://evil.example.org",
                           "Access-Control-Request-Method: GET").status());

    Reply r = send("OPTIONS /api/items", "Origin: " + UI, "Access-Control-Request-Method: PUT");
    assertEquals(403, r.status());
    assertNull(r.header("Access-Control-Allow-Origin"));
  }

  @Test
  public void testConfiguredHeaders() throws IOException {
    cors.allowOrigins(UI)
        .allowHeaders("Content-Type", "Authorization")
        .exposeHeaders("X-Total-Count")
        .maxAge(java.time.Duration.ofHours(1));
    Reply r = send("OPTIONS /api/items",
                   "Origin: " + UI,
                   "Access-Control-Request-Method: GET",
                   "Access-Control-Request-Headers: x-other");
    assertEquals("Content-Type, Authorization", r.header("Access-Control-Allow-Headers"));
    assertEquals("3600", r.header("Access-Control-Max-Age"));
    assertNull(r.header("Access-Control-Expose-Headers"));

    assertEquals("X-Total-Count",
                 send("GET /api/items", "Origin: " + UI).header("Access-Control-Expose-Headers"));
  }

  @Test
  public void testAnyOrigin() throws IOException {
    cors.allowAnyOrigin();
    Reply r = send("GET /api/items", "Origin: https://whoever.example.org");
    assertEquals("*", r.header("Access-Control-Allow-Origin"));
    assertFalse(r.headers("Vary").contains("Origin"));
  }

  /**
   * Browsers refuse "*" with credentials.
   */
  @Test
  public void testCredentials() throws IOException {
    cors.allowAnyOrigin().allowCredentials(true);
    Reply r = send("GET /api/items", "Origin: " + UI);
    assertEquals(UI, r.header("Access-Control-Allow-Origin"));
    assertEquals("true", r.header("Access-Control-Allow-Credentials"));
    assertTrue(r.headers("Vary").contains("Origin"));

    Reply pre = send("OPTIONS /api/items", "Origin: " + UI, "Access-Control-Request-Method: GET");
    assertEquals(UI, pre.header("Access-Control-Allow-Origin"));
    assertEquals("true", pre.header("Access-Control-Allow-Credentials"));
  }

  private Reply send(String requestLine, String... headers) throws IOException {
    return RawHttp.send(app.getRootUrl(), requestLine, headers);
  }
}
