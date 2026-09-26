package flask.test;

import flak.App;
import flak.AppFactory;
import flak.annotations.QueryParam;
import flak.annotations.Route;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Test method parameters automatically extracted from query string.
 * Those require using @QueryParam to avoid conflict with path or body parameters.
 */
public class QueryAnnotationTest extends AbstractAppTest {

  public enum Color {RED, GREEN}

  @Route("/string")
  public String string(@QueryParam("foo") String foo) {
    return String.valueOf(foo);
  }

  @Route("/string_array")
  public String string_array(@QueryParam("foo") String[] foo) {
    return Arrays.toString(foo);
  }

  @Route("/str_int")
  public String str_int(@QueryParam("foo") String string,
                        @QueryParam("num") Integer numeric) {
    return string + "-" + numeric;
  }

  @Route("/primitives")
  public String primitives(@QueryParam("i") int i,
                           @QueryParam("l") long l,
                           @QueryParam("d") double d,
                           @QueryParam("b") boolean b) {
    return i + " " + l + " " + d + " " + b;
  }

  @Route("/boxed")
  public String boxed(@QueryParam("i") Integer i,
                      @QueryParam("l") Long l,
                      @QueryParam("d") Double d,
                      @QueryParam("b") Boolean b,
                      @QueryParam("c") Color c) {
    return i + " " + l + " " + d + " " + b + " " + c;
  }

  @Route("/defaults")
  public String defaults(@QueryParam(value = "s", defaultValue = "none") String s,
                         @QueryParam(value = "i", defaultValue = "50") int i,
                         @QueryParam(value = "b", defaultValue = "true") boolean b,
                         @QueryParam(value = "c", defaultValue = "GREEN") Color c,
                         @QueryParam(value = "a", defaultValue = "x") String[] a) {
    return s + " " + i + " " + b + " " + c + " " + Arrays.toString(a);
  }

  @Route("/required")
  public String required(@QueryParam(value = "s", required = true) String s,
                         @QueryParam(value = "i", required = true) int i) {
    return s + " " + i;
  }

  @Route("/required_array")
  public String required_array(@QueryParam(value = "a", required = true) String[] a) {
    return Arrays.toString(a);
  }

  @Test
  public void testRequired() throws Exception {
    assertEquals("a 1", client.get("/required?s=a&i=1"));
    // an empty string is a value, an empty number is not
    assertEquals(" 1", client.get("/required?s=&i=1"));
    TestUtil.assertFails(() -> client.get("/required?i=1"), "400 Missing query parameter s");
    TestUtil.assertFails(() -> client.get("/required?s=a&i="), "400 Missing query parameter i");

    assertEquals("[x]", client.get("/required_array?a=x"));
    TestUtil.assertFails(() -> client.get("/required_array"), "400 Missing query parameter a");
  }

  @Test
  public void testRequiredWithDefaultFailsScan() {
    AppFactory factory = TestUtil.getFactory();
    App other = factory.createApp();
    TestUtil.assertFails(() -> other.scan(new Object() {
      @Route("/bad")
      public String bad(@QueryParam(value = "i", required = true, defaultValue = "1") int i) {
        return "";
      }
    }), "Query parameter i cannot be both required and have a default value");
  }

  @Test
  public void testQueryParameters() throws Exception {
    assertEquals("foobar", client.get("/string?num=42&foo=foobar"));
    assertEquals("[a, b, c]", client.get("/string_array?num=42&foo=a&foo=b&foo=c"));
    assertEquals("foobar-42", client.get("/str_int?num=42&foo=foobar"));
  }

  @Test
  public void testMissingValues() throws Exception {
    assertEquals("null", client.get("/string"));
    assertEquals("", client.get("/string?foo="));
    assertEquals("[]", client.get("/string_array"));
    assertEquals("null-null", client.get("/str_int"));
    assertEquals("-1 -1 -1.0 false", client.get("/primitives"));
    assertEquals("-1 -1 -1.0 false", client.get("/primitives?i=&l=&d=&b="));
    assertEquals("null null null null null", client.get("/boxed"));
  }

  @Test
  public void testTypes() throws Exception {
    assertEquals("42 9000000000 1.5 true", client.get("/primitives?i=42&l=9000000000&d=1.5&b=true"));
    assertEquals("42 9000000000 1.5 false RED", client.get("/boxed?i=42&l=9000000000&d=1.5&b=FALSE&c=RED"));
  }

  @Test
  public void testDefaultValues() throws Exception {
    assertEquals("none 50 true GREEN [x]", client.get("/defaults"));
    assertEquals("foo 3 false RED [y, z]", client.get("/defaults?s=foo&i=3&b=false&c=RED&a=y&a=z"));
  }

  @Test
  public void testInvalidValues() {
    TestUtil.assertFails(() -> client.get("/primitives?i=abc"), "400 Invalid value for query parameter i: abc");
    TestUtil.assertFails(() -> client.get("/boxed?l=1.5"), "400 Invalid value for query parameter l: 1.5");
    TestUtil.assertFails(() -> client.get("/boxed?b=yes"), "400 Invalid value for query parameter b: yes");
    TestUtil.assertFails(() -> client.get("/boxed?c=BLUE"), "400 Invalid value for query parameter c: BLUE");
  }

  @Test
  public void testInvalidDefaultFailsScan() {
    AppFactory factory = TestUtil.getFactory();
    App other = factory.createApp();
    TestUtil.assertFails(() -> other.scan(new Object() {
      @Route("/bad")
      public String bad(@QueryParam(value = "i", defaultValue = "abc") int i) {
        return "";
      }
    }), "Invalid default value for query parameter i: abc");
  }

  @Test
  public void testUnsupportedTypeFailsScan() {
    AppFactory factory = TestUtil.getFactory();
    App other = factory.createApp();
    TestUtil.assertFails(() -> other.scan(new Object() {
      @Route("/bad")
      public String bad(@QueryParam("o") Object o) {
        return "";
      }
    }), "Unsupported type for a query parameter");
  }
}
