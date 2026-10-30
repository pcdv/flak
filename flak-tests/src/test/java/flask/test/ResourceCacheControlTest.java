package flask.test;

import flak.ResourceOptions;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The Cache-Control header set in the options is sent with the files served,
 * from a directory or the classpath, and not with a 404.
 */
public class ResourceCacheControlTest extends AbstractAppTest {

  private static final String IMMUTABLE = "public, max-age=31536000, immutable";

  private HttpURLConnection open(String path) throws Exception {
    return (HttpURLConnection) new URL(app.getRootUrl() + path).openConnection();
  }

  @Test
  public void dir() throws Exception {
    Path dir = Files.createTempDirectory("flak");
    Files.write(dir.resolve("app-1234.js"), "x".getBytes());
    app.serveDir("/assets", dir.toFile(), new ResourceOptions().cacheControl(IMMUTABLE));

    HttpURLConnection con = open("/assets/app-1234.js");
    assertEquals(200, con.getResponseCode());
    assertEquals(IMMUTABLE, con.getHeaderField("Cache-Control"));

    con = open("/assets/missing.js");
    assertEquals(404, con.getResponseCode());
    assertNull(con.getHeaderField("Cache-Control"));
  }

  @Test
  public void classpath() throws Exception {
    app.serveClasspath("/static", "/test-resources", new ResourceOptions().cacheControl("no-cache"));
    app.serveClasspath("/plain", "/test-resources");

    HttpURLConnection con = open("/static/foo.html");
    assertEquals(200, con.getResponseCode());
    assertEquals("no-cache", con.getHeaderField("Cache-Control"));

    con = open("/plain/foo.html");
    assertEquals(200, con.getResponseCode());
    assertNull(con.getHeaderField("Cache-Control"));
  }
}
