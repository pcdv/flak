package flask.test;

import java.io.IOException;

import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class AmbiguousRouteTest extends AbstractAppTest {

  @Route("/api/file")
  public String getAll() {
    return "ALL";
  }

  @Route("/api/file/*path")
  public String getFile(String path) {
    return "PATH:[" + path + "]";
  }

  @Test
  public void test() throws IOException {
    Assert.assertEquals("ALL", client.get("/api/file"));
    Assert.assertEquals("ALL", client.get("/api/file/"));
    Assert.assertEquals("PATH:[foo]", client.get("/api/file/foo"));
  }
}
