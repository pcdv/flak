package flask.test;

import flak.annotations.Compress;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/**
 * The level and buffer size of the gzip compression are set on the app: a
 * faster level sends more bytes, the same content.
 */
@Compress
public class CompressionLevelTest extends AbstractAppTest {

  private static final String DATA = data();

  /** Text that compresses differently with each level, unlike a run of one char. */
  private static String data() {
    Random random = new Random(42);
    StringBuilder sb = new StringBuilder();
    while (sb.length() < 200_000)
      sb.append("{\"id\":").append(random.nextInt(100_000))
        .append(",\"name\":\"item").append(random.nextInt(1000)).append("\"},");
    return sb.toString();
  }

  @Route("/api/data")
  public String getData() {
    return DATA;
  }

  /** The response as sent, still compressed. */
  private byte[] getRaw() throws IOException {
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + "/api/data").openConnection();
    con.setRequestProperty("Accept-Encoding", "gzip");
    Assert.assertEquals("gzip", con.getContentEncoding());
    try (InputStream in = con.getInputStream()) {
      return in.readAllBytes();
    }
  }

  private static String gunzip(byte[] bytes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
      in.transferTo(out);
    }
    return out.toString("UTF-8");
  }

  @Test
  public void levelAndBufferSize() throws Exception {
    byte[] byDefault = getRaw();
    Assert.assertEquals(DATA, gunzip(byDefault));

    app.setCompressionLevel(1);
    app.setCompressionBufferSize(8192);
    byte[] fast = getRaw();
    Assert.assertEquals(DATA, gunzip(fast));

    app.setCompressionLevel(9);
    byte[] small = getRaw();
    Assert.assertEquals(DATA, gunzip(small));

    Assert.assertTrue(fast.length + " > " + byDefault.length, fast.length > byDefault.length);
    Assert.assertTrue(small.length + " <= " + byDefault.length, small.length <= byDefault.length);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidLevel() {
    app.setCompressionLevel(10);
  }
}
