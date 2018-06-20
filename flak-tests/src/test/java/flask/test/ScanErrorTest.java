package flask.test;

import flak.annotations.Route;
import org.junit.Test;

/**
 * Check that invalid route handlers generate an understandable error.
 *
 * @author pcdv
 */
public class ScanErrorTest extends AbstractAppTest {

  @Test
  public void testMissingMethodArgument() {
    TestUtil.assertFails(() -> app.scan(new Object() {
      @Route("/foo/:a/:b")
      public void foo(String a) {}
    }), "Not enough method parameters");
  }

  @Test
  public void testExtraMethodArgument() {
    TestUtil.assertFails(() -> app.scan(new Object() {
      @Route("/foo/:a/:b")
      public void foo(String a, String b, String c) {}
    }), "Too many method parameters");
  }
}
