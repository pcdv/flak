package flask.test;

import flak.App;
import flak.AppFactory;
import flak.annotations.Route;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 * Checks how the root of a served directory, and its subdirectories, are
 * served: a directory without its trailing slash is redirected to it, so that
 * the relative links of its index.html resolve, and serves its index.html.
 */
public class StaticRootTest {

  private Path dir;
  private App app;

  @Before
  public void setUp() throws Exception {
    dir = Files.createTempDirectory("flak");
    Files.writeString(dir.resolve("index.html"), "INDEX");
    Files.createDirectories(dir.resolve("sub"));
    Files.writeString(dir.resolve("sub/index.html"), "SUB");
    Files.createDirectories(dir.resolve("empty"));
  }

  @After
  public void tearDown() {
    if (app != null)
      app.stop();
  }

  private App createApp() {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    return app = factory.createApp();
  }

  @Test
  public void testServeDir() throws Exception {
    createApp();
    app.serveDir("/static", dir.toFile());
    app.start();

    assertEquals("302 /static/", get("/static"));
    assertEquals("302 /static/?x=1", get("/static?x=1"));
    assertEquals("200 INDEX", get("/static/"));
    assertEquals("302 /static/sub/", get("/static/sub"));
    assertEquals("200 SUB", get("/static/sub/"));
    assertEquals("404 Not found", get("/static/empty/"));
    assertEquals("404 Not found", get("/static/missing"));
  }

  @Test
  public void testServePathFromClasspath() throws Exception {
    createApp();
    app.serveClasspath("/cp", "/test-resources");
    app.start();

    assertEquals("302 /cp/", get("/cp"));
    // no index.html there
    assertEquals("404 Not found", get("/cp/"));
    assertEquals("200 FOO", get("/cp/foo.html"));
  }

  @Test
  public void testServeRoot() throws Exception {
    createApp();
    app.serveDir("/", dir.toFile());
    app.start();

    assertEquals("200 INDEX", get("/"));
    assertEquals("302 /sub/", get("/sub"));
    assertEquals("200 SUB", get("/sub/"));
  }

  public static class RootRoute {
    @Route("/")
    public String root() {
      return "ROUTE";
    }
  }

  /**
   * A route of the app wins over the index of the resources, whichever was
   * registered first.
   */
  @Test
  public void testRouteScannedAfterResourcesWins() throws Exception {
    createApp();
    app.serveDir("/", dir.toFile());
    app.scan(new RootRoute());
    app.start();
    assertEquals("200 ROUTE", get("/"));
    assertEquals("200 SUB", get("/sub/"));
  }

  @Test
  public void testRouteScannedBeforeResourcesWins() throws Exception {
    createApp();
    app.scan(new RootRoute());
    app.serveDir("/", dir.toFile());
    app.start();
    assertEquals("200 ROUTE", get("/"));
  }

  private String get(String path) throws Exception {
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + path).openConnection();
    con.setInstanceFollowRedirects(false);
    int code = con.getResponseCode();
    if (code == 302)
      return code + " " + con.getHeaderField("Location");
    byte[] body = (code < 400 ? con.getInputStream() : con.getErrorStream()).readAllBytes();
    return code + " " + new String(body, StandardCharsets.UTF_8);
  }
}
