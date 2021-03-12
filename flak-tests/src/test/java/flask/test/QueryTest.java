package flask.test;

import java.io.IOException;

import flak.Query;
import flak.Request;
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

  @Test
  public void testShowParams() throws Exception {
    assertEquals("[]", client.get("/showParams"));
    assertEquals("[a=b]", client.get("/showParams?a=b"));
    assertEquals("[a=b, c=d]", client.get("/showParams?a=b&c=d"));
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
