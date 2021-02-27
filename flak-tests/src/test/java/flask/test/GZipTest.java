package flask.test;

import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Experimental: trying to make HttpServer output some "gzip" content encoding.
 * Disabled because does not work.
 */
@Ignore
public class GZipTest extends AbstractAppTest {

  @Route("/gzip")
  public void getData(Response r) throws IOException {
    r.addHeader("Content-encoding", "deflate");
    GZIPOutputStream out = new GZIPOutputStream(r.getOutputStream());
    out.write("Hello, world".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void testGZipContent() throws IOException {
    Assert.assertEquals("Hello, world", client.get("/gzip"));
  }
}
