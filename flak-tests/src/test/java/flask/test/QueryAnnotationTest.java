package flask.test;

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

  @Route("/string")
  public String string(@QueryParam("foo") String foo) {
    return foo;
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

  @Test
  public void testQueryParameters() throws Exception {
    assertEquals("foobar", client.get("/string?num=42&foo=foobar"));
    assertEquals("[a, b, c]", client.get("/string_array?num=42&foo=a&foo=b&foo=c"));
    assertEquals("foobar-42", client.get("/str_int?num=42&foo=foobar"));
  }
}
