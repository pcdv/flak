package flask.test;

import java.io.IOException;

import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class IntArgTest extends AbstractAppTest {

  @Route("/int/:id")
  public String getInt(int id) {
    return "" + id;
  }

  @Test
  public void testIntArg() throws IOException {
    Assert.assertEquals("42", client.get("/int/42"));
    Assert.assertEquals("-3", client.get("/int/-3"));
  }

  /**
   * There is no such resource, rather than a server error: 404, as JAX-RS
   * answers a path parameter it cannot convert.
   */
  @Test
  public void testNotANumber() {
    TestUtil.assertFails(() -> client.get("/int/abc"), "404");
    TestUtil.assertFails(() -> client.get("/int/99999999999"), "404");
  }
}
