package flask.test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import flak.Request;
import flak.annotations.MaxBodySize;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A handler that streams a big request body straight into a file, without it
 * ever being held in memory.
 * <p>
 * The body is deliberately larger than the memory the test JVM is given (see
 * the limits set in build.gradle): a backend that buffered the request before
 * calling the handler would fail here, which is exactly what the netty one
 * used to do.
 */
public class BigInputTest extends AbstractAppTest {

  private static final long SIZE = 128L * 1024 * 1024;

  @Route("/upload")
  @Post
  @MaxBodySize(MaxBodySize.UNLIMITED)
  public String upload(Request r) throws Exception {
    File tmp = File.createTempFile("flak-upload", ".bin");
    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
      InputStream in = r.getInputStream();
      byte[] buf = new byte[8192];
      for (int n = in.read(buf); n > 0; n = in.read(buf))
        out.write(buf, 0, n);
    }
    try {
      return String.valueOf(tmp.length());
    }
    finally {
      tmp.delete();
    }
  }

  @Test
  public void bodyBiggerThanMemoryIsStreamed() throws Exception {
    HttpURLConnection c = (HttpURLConnection) URI.create(app.getRootUrl() + "/upload")
                                                 .toURL()
                                                 .openConnection();
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    // stream the request out too, so that the client is not the one to blow up
    c.setFixedLengthStreamingMode(SIZE);

    byte[] chunk = new byte[8192];
    try (OutputStream out = c.getOutputStream()) {
      for (long sent = 0; sent < SIZE; sent += chunk.length)
        out.write(chunk);
    }

    assertEquals(200, c.getResponseCode());
    try (InputStream in = c.getInputStream()) {
      assertEquals(String.valueOf(SIZE), new String(in.readAllBytes()).trim());
    }
  }
}
