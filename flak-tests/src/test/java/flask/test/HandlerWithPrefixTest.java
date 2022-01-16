package flask.test;

import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * Shows how a single handler class can be reused at different paths. Also tests
 * that handler methods inherited from the superclass are properly scanned.
 */
public class HandlerWithPrefixTest extends AbstractAppTest {

  /**
   * This handler will be reused at several paths.
   */
  public static class ParametrizedHandler {

    private final String value;

    public ParametrizedHandler(String value) {
      this.value = value;
    }

    @Route("/value")
    public String getValue() {
      return value;
    }
  }

  public static class SubClass extends ParametrizedHandler {
    public SubClass(String value) {
      super(value);
    }
  }

  @Test
  public void testIt() throws Exception {
    app.scan(new ParametrizedHandler("foo"), "/foo");
    app.scan(new SubClass("bar"), "/bar");

    Assert.assertEquals("foo", client.get("/foo/value"));
    Assert.assertEquals("bar!", client.get("/bar/value"));
  }
}
