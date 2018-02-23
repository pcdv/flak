package flask.test;

import java.io.IOException;

import flak.annotations.Route;
import org.junit.Test;

/**
 * Reproduce ArrayIndexOutOfBoundsException
 *
 * @author pcdv
 */
public class RouteTest2 extends AbstractAppTest {

  @Route("/api/env/instance/:instanceId/file/*path")
  public String testSplat(String id, String path) {
    return id + "--" + path;
  }

  @Test
  public void testComplexSplat() throws IOException {
    TestUtil.assertFails(() -> client.get("/api/env/instance/42"), "");
  }
}
