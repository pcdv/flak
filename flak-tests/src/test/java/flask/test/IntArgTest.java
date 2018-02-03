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
  }
}
