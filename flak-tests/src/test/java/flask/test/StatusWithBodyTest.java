package flask.test;

import flak.Response;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * Checks that a status set by a handler is kept when it also returns a body.
 */
public class StatusWithBodyTest extends AbstractAppTest {

  @Route("/string")
  @Post
  public String string(Response r) {
    r.setStatus(201);
    return "created";
  }

  @Route("/bytes")
  @Post
  public byte[] bytes(Response r) {
    r.setStatus(202);
    return "accepted".getBytes();
  }

  @Route("/stream")
  @Post
  public InputStream stream(Response r) {
    r.setStatus(203);
    return new ByteArrayInputStream("stream".getBytes());
  }

  @Route("/default")
  @Post
  public String byDefault() {
    return "ok";
  }

  @Test
  public void testStatusIsKept() throws Exception {
    assertStatus("/string", 201, "created");
    assertStatus("/bytes", 202, "accepted");
    assertStatus("/stream", 203, "stream");
    assertStatus("/default", 200, "ok");
  }

  private void assertStatus(String path, int status, String body) throws Exception {
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + path).openConnection();
    con.setRequestMethod("POST");
    assertEquals(path, status, con.getResponseCode());
    assertEquals(path, body, new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }
}
