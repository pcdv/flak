package flask.test;

import flak.Request;
import flak.annotations.Route;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Reads cookies as browsers send them, including those that RFC 6265 does
 * not allow but that are common in practice.
 */
public class CookieTest extends AbstractAppTest {

  @Route("/cookie/:name")
  public String cookie(String name, Request r) {
    return String.valueOf(r.getCookie(name));
  }

  private String get(String header, String name) throws IOException {
    client.addHeader("Cookie", header);
    return client.get("/cookie/" + name);
  }

  @Test
  public void testSeveralCookies() throws IOException {
    assertEquals("1", get("a=1; b=2; c=3", "a"));
    assertEquals("2", get("a=1; b=2; c=3", "b"));
    assertEquals("3", get("a=1; b=2; c=3", "c"));
    assertEquals("null", get("a=1; b=2; c=3", "d"));
  }

  @Test
  public void testNamePrefixOfAnother() throws IOException {
    assertEquals("2", get("ab=1; a=2", "a"));
    assertEquals("null", get("ab=1", "a"));
  }

  @Test
  public void testValueWithEquals() throws IOException {
    assertEquals("dG9rZW4=", get("t=dG9rZW4=", "t"));
  }

  @Test
  public void testEmptyValue() throws IOException {
    assertEquals("", get("a=; b=2", "a"));
    assertEquals("2", get("a=; b=2", "b"));
  }

  /**
   * A cookie without '=', e.g. set by a script as document.cookie = "x".
   */
  @Test
  public void testWithoutValue() throws IOException {
    assertEquals("null", get("flag; b=2", "flag"));
    assertEquals("2", get("flag; b=2", "b"));
  }

  /**
   * Browsers send cookies with spaces, commas or double quotes in their
   * value, e.g. JSON set by a script, although RFC 6265 forbids them.
   */
  @Test
  public void testLaxValues() throws IOException {
    assertEquals("{\"a\":1,\"b\":2}", get("prefs={\"a\":1,\"b\":2}; s=x", "prefs"));
    assertEquals("x", get("prefs={\"a\":1,\"b\":2}; s=x", "s"));
    assertEquals("hello world", get("m=hello world; s=x", "m"));
  }

  /**
   * The value is returned as sent, quotes included.
   */
  @Test
  public void testQuotedValue() throws IOException {
    assertEquals("\"abc\"", get("q=\"abc\"", "q"));
  }

  /**
   * Browsers send the cookie with the most specific path first.
   */
  @Test
  public void testFirstOfDuplicatesWins() throws IOException {
    assertEquals("1", get("a=1; a=2", "a"));
  }

  @Test
  public void testNoCookie() throws IOException {
    assertEquals("null", client.get("/cookie/a"));
  }
}
