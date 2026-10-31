package flask.test;

import flak.ResourceOptions;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The files served carry a Last-Modified header, and a request that gives it
 * back in If-Modified-Since gets a 304 without a body, as long as the file
 * has not changed.
 */
public class ResourceLastModifiedTest extends AbstractAppTest {

  private static final String OLD = "Sun, 06 Nov 1994 08:49:37 GMT";

  private HttpURLConnection open(String path, String since) throws Exception {
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + path).openConnection();
    if (since != null)
      con.setRequestProperty("If-Modified-Since", since);
    return con;
  }

  @Test
  public void dir() throws Exception {
    Path dir = Files.createTempDirectory("flak");
    Path file = dir.resolve("a.js");
    Files.write(file, "x".getBytes());
    Files.setLastModifiedTime(file, FileTime.fromMillis(784111777000L));
    app.serveDir("/assets", dir.toFile(), new ResourceOptions().cacheControl("no-cache"));

    HttpURLConnection con = open("/assets/a.js", null);
    assertEquals(200, con.getResponseCode());
    assertEquals(OLD, con.getHeaderField("Last-Modified"));

    con = open("/assets/a.js", OLD);
    assertEquals(304, con.getResponseCode());
    assertEquals(OLD, con.getHeaderField("Last-Modified"));
    assertEquals("no-cache", con.getHeaderField("Cache-Control"));
    assertEquals(0, con.getContentLengthLong() < 0 ? 0 : con.getContentLengthLong());

    // the file changes
    Files.setLastModifiedTime(file, FileTime.fromMillis(784111777000L + 5000));
    con = open("/assets/a.js", OLD);
    assertEquals(200, con.getResponseCode());
    assertEquals("Sun, 06 Nov 1994 08:49:42 GMT", con.getHeaderField("Last-Modified"));
    assertEquals("no-cache", con.getHeaderField("Cache-Control"));
    assertEquals("x", new String(con.getInputStream().readAllBytes()));

    // an invalid date is ignored
    assertEquals(200, open("/assets/a.js", "yesterday").getResponseCode());

    // a missing file is not found, whatever the date
    con = open("/assets/missing.js", OLD);
    assertEquals(404, con.getResponseCode());
    assertNull(con.getHeaderField("Last-Modified"));
  }

  @Test
  public void classpath() throws Exception {
    app.serveClasspath("/static", "/test-resources");

    HttpURLConnection con = open("/static/foo.html", null);
    assertEquals(200, con.getResponseCode());
    String modified = con.getHeaderField("Last-Modified");
    assertNotNull(modified);

    con = open("/static/foo.html", modified);
    assertEquals(304, con.getResponseCode());
    assertEquals(modified, con.getHeaderField("Last-Modified"));
    assertEquals(200, open("/static/foo.html", OLD).getResponseCode());
  }
}
