package flask.test;

import flak.Response;
import flak.annotations.Compress;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class BrokenStreamTest extends AbstractAppTest {

  public static final int FAIL_AFTER_N_BYTES = 10000;

  @Route("/download")
  public InputStream download(Response r) {
    return openStream(r);
  }

  @Compress
  @Route("/downloadZipped")
  public InputStream downloadZipped(Response r) {
    return openStream(r);
  }

  @Route("/ok")
  public String ok() {
    return "ok";
  }

  private static InputStream openStream(Response r) {
    String fileName = "foo.bin";

    r.addHeader("content-disposition",
                "attachment; filename=\"" + fileName + "\"");

    return new InputStream() {
      private int count;

      @Override
      public int read() throws IOException {
        if (count++ > FAIL_AFTER_N_BYTES)
          throw new IOException("Something bad happened");
        return 0;
      }
    };
  }

  /**
   * Check that if InputStream returned by a handler throws an exception, the
   * HTTP download will fail.
   */
  @Test
  public void testIOExceptionInInputStream() throws IOException {
    // HttpURLConnection must throw "Premature EOF" (abrupt TCP close, not a clean response).
    TestUtil.assertFails(() -> client.get("/download"), "Premature EOF");
    client.addHeader("Accept-Encoding", "gzip");
    TestUtil.assertFails(() -> client.get("/downloadZipped"), "Premature EOF");

    // Raw socket (curl/browser behavior): connection must close within timeout, not hang.
    rawGetWithConnectionClose("/download");

    // Server remains usable after a broken stream.
    Assert.assertEquals("ok", client.get("/ok"));
  }

  private String rawGetWithConnectionClose(String path) throws IOException {
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), app.getServer().getPort())) {
      socket.setSoTimeout(4000);

      OutputStream out = socket.getOutputStream();
      out.write(("GET " + path + " HTTP/1.1\r\n"
        + "Host: localhost\r\n"
        + "Connection: close\r\n"
        + "\r\n").getBytes(StandardCharsets.US_ASCII));
      out.flush();

      ByteArrayOutputStream body = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      InputStream in = socket.getInputStream();
      try {
        while (true) {
          int n = in.read(buf);
          if (n < 0)
            break;
          body.write(buf, 0, n);
        }
      }
      catch (SocketTimeoutException e) {
        Assert.fail("Server did not terminate broken chunked response within timeout");
      }
      return body.toString(StandardCharsets.ISO_8859_1.name());
    }
  }
}
