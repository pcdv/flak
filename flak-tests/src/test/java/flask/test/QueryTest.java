package flask.test;

import java.io.IOException;
import java.util.Arrays;

import flak.Form;
import flak.Query;
import flak.Request;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Misc query string related tests.
 */
public class QueryTest extends AbstractAppTest {

  @Route("/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }

  @Test
  public void queryIsNotCapturedInArg() throws Exception {
    assertEquals("Hello world", client.get("/hello/world?foo=bar"));
  }

  @Route("/hello2")
  public String helloQuery() {
    return "Hello " + app.getRequest().getQuery().get("name", null);
  }

  @Route("/helloTyped")
  public String helloTyped(Query q) {
    return "Hello " + q.getBool("bool", false) + " " + q.getInt("num", 0);
  }

  @Route("/showParams")
  public String showParams(Query q) {
    return q.parameters().toString();
  }

  @Route("/queryString")
  public String queryString(Request req) {
    return req.getQueryString();
  }

  @Route("/form")
  @Post
  public String showFormParams(Form f) {
    return f.parameters() + " " + Arrays.toString(f.getArray("a b"));
  }

  @Test
  public void testShowParams() throws Exception {
    assertEquals("[a=+]", client.get("/showParams?a=%2B"));
    assertEquals("[a= ]", client.get("/showParams?a=%20"));
    // as HTML forms encode a space
    assertEquals("[a= ]", client.get("/showParams?a=+"));
    assertEquals("[a=x y]", client.get("/showParams?a=x+y"));
    assertEquals("[]", client.get("/showParams"));
    assertEquals("[a=b]", client.get("/showParams?a=b"));
    assertEquals("[a=b, c=d]", client.get("/showParams?a=b&c=d"));
    assertEquals("[a=1, a=2, a=3]", client.get("/showParams?a=1&a=2&a=3"));
  }

  /**
   * A value is decoded after the query string is split, not before: an
   * encoded '&' or '=' belongs to it.
   */
  @Test
  public void testEncodedSeparators() throws Exception {
    assertEquals("[url=/data?x=1&y=2]", client.get("/showParams?url=%2Fdata%3Fx%3D1%26y%3D2"));
    assertEquals("[a b=c&d, é=ü]", client.get("/showParams?a+b=c%26d&%C3%A9=%C3%BC"));
  }

  @Test
  public void testQueryStringIsRaw() throws Exception {
    assertEquals("a=x+y&b=%26", client.get("/queryString?a=x+y&b=%26"));
  }

  @Test
  public void testFormIsDecodedLikeQuery() throws Exception {
    assertEquals("[a b=1, a b=x&y] [1, x&y]", client.post("/form", "a+b=1&a%20b=x%26y"));
  }

  /**
   * Only a form can get there: both backends reject such a query string
   * before we see it, as java.net.URI does.
   */
  @Test
  public void testMalformedEncoding() {
    TestUtil.assertFails(() -> client.post("/form", "a=%zz"), "400 Malformed url-encoded data: %zz");
  }

  @Test
  public void testQueryWithTypes() throws Exception {
    assertEquals("Hello false 0", client.get("/helloTyped"));
    assertEquals("Hello true 1", client.get("/helloTyped?bool=true&num=1"));
  }

  @Test
  public void trailingSlashNotCausingAnyTrouble() throws Exception {
    assertEquals("Hello world", client.get("/hello2/?name=world"));
    assertEquals("Hello world", client.get("/hello2?name=world"));
    assertEquals("Hello null", client.get("/hello2/?name2=world"));
    assertEquals("Hello null", client.get("/hello2?name2=world"));
  }

  @Test
  public void queryFromAppRequest() throws Exception {
    assertEquals("Hello world", client.get("/hello2?name=world"));
  }

  @Route("/hello_bytes")
  public byte[] byteArrayHelloWorld(Request req) {
    return ("Hello " + req.getQuery().get("name", null)).getBytes();
  }

  @Test
  public void testReturnByteArray() throws Exception {
    assertEquals("Hello world", client.get("/hello_bytes?name=world"));
  }

  @Route("/hello/request")
  public String getQueryFromRequest(Request req) {
    return "Hello " + req.getQuery().get("name", "???");
  }

  @Test
  public void queryStringFromInjectedRequest() throws IOException {
    assertEquals("Hello world", client.get("/hello/request?name=world"));
    assertEquals("Hello ???", client.get("/hello/request?foo=bar"));
    assertEquals("Hello ???", client.get("/hello/request?foo=bar&baz=x"));
    assertEquals("Hello ???", client.get("/hello/request?"));
    assertEquals("Hello ???", client.get("/hello/request"));
  }

  @Route("/hello/request/injected")
  public String getInjectedQuery(Query req) {
    return "Hello " + req.get("name");
  }

  @Test
  public void queryIsInjectedInMethod() throws IOException {
    assertEquals("Hello injected", client.get("/hello/request/injected?name=injected"));
  }
}
