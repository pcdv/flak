package flask.test;

import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IndexTest extends AbstractAppTest {

  @Route("/index.html")
  public String indexHtml() {
    return "Index";
  }

  @Route("/")
  public String index() {
    return "Index";
  }

  @Test
  public void testIndexHtml() throws Exception {
    Assert.assertEquals("Index", client.get("/index.html"));
    Assert.assertEquals("Index", client.get("/"));
  }
}
