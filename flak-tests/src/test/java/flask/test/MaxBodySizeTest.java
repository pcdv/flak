package flask.test;

import java.io.InputStream;

import flak.Request;
import flak.annotations.MaxBodySize;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The size of a request body is capped by default, and a handler that means
 * to accept a big one says so.
 */
public class MaxBodySizeTest extends AbstractAppTest {

  @Override
  protected void preScan() {
    app.setMaxBodySize(1024);
  }

  private static String drain(Request r) throws Exception {
    InputStream in = r.getInputStream();
    long n = 0;
    byte[] buf = new byte[4096];
    for (int read = in.read(buf); read > 0; read = in.read(buf))
      n += read;
    return String.valueOf(n);
  }

  @Route("/capped")
  @Post
  public String capped(Request r) throws Exception {
    return drain(r);
  }

  @Route("/generous")
  @Post
  @MaxBodySize(64 * 1024)
  public String generous(Request r) throws Exception {
    return drain(r);
  }

  @Route("/unlimited")
  @Post
  @MaxBodySize(MaxBodySize.UNLIMITED)
  public String unlimited(Request r) throws Exception {
    return drain(r);
  }

  private String body(int size) {
    StringBuilder s = new StringBuilder(size);
    for (int i = 0; i < size; i++)
      s.append('x');
    return s.toString();
  }

  @Test
  public void bodyWithinTheAppLimitIsAccepted() throws Exception {
    assertEquals("1024", client.post("/capped", body(1024)));
  }

  @Test
  public void bodyOverTheAppLimitIsRejected() {
    TestUtil.assertFails(() -> client.post("/capped", body(1025)), "413");
  }

  @Test
  public void handlerCanRaiseTheLimit() throws Exception {
    assertEquals("32768", client.post("/generous", body(32 * 1024)));
  }

  @Test
  public void handlerLimitIsStillALimit() {
    TestUtil.assertFails(() -> client.post("/generous", body(64 * 1024 + 1)), "413");
  }

  /**
   * The case this exists for: a handler that streams a big body somewhere and
   * must not be capped, e.g. straight into a file.
   */
  @Test
  public void handlerCanWaiveTheLimit() throws Exception {
    assertEquals("1048576", client.post("/unlimited", body(1024 * 1024)));
  }
}
