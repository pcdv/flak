package flask.test;

import flak.Response;
import flak.annotations.Compress;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Reproduce inconsistencies when buffer is flushed before headers and status
 * are set.
 */
@Compress
public class BigOutputTest extends AbstractAppTest {

  @Route("/api/data")
  public String getBigString() {
    final char[] chars = new char[32768];
    Arrays.fill(chars, 'X');
    return new String(chars);
  }

  @Route("/api/data2")
  public void writeBigString(Response r) throws IOException {
    r.getOutputStream().write(getBigString().getBytes());
  }

  @Test
  public void test() throws Exception {
    Assert.assertEquals(getBigString(), client.get("/api/data"));
    Assert.assertEquals("gzip", client.getLastConnection().getContentEncoding());
    Assert.assertEquals(getBigString(), client.get("/api/data2"));
    Assert.assertNull(client.getLastConnection().getContentEncoding());
  }
}
