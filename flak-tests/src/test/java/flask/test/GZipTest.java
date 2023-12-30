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
 * Experimental: make HttpServer output some "gzip" content encoding.
 * Next step is to automatically check that the client accepts gzip and compress
 * on the fly. Maybe need a heuristic to decide when to compress, or use an explicit
 * setting and annotation to tweak behavior per endpoint.
 */
public class GZipTest extends AbstractAppTest {

  @Route("/gzip")
  public void getData(Response r) throws IOException {
    r.addHeader("Content-Encoding", "gzip");
    r.addHeader("Content-type", "text/html");

    GZIPOutputStream out = new GZIPOutputStream(r.getOutputStream());
    out.write("Hello, world".getBytes(StandardCharsets.UTF_8));
    out.close();
  }

  @Test
  public void testGZipContent() throws Exception {
    Assert.assertEquals("Hello, world", client.get("/gzip"));
  }
}
