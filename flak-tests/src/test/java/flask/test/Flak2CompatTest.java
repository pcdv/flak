package flask.test;

import flak.App;
import flak.AppFactory;
import flak.Request;
import flak.annotations.Route;
import flak.plugin.resource.FlakResourceImpl;
import org.junit.After;
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
 * Checks the deprecated classes kept so that code written for flak 2.x runs
 * and compiles unchanged, used as in the projects still calling them.
 */
@SuppressWarnings("deprecation")
public class Flak2CompatTest {

  private App app;

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

  public static class Index {
    @Route("/h3")
    public void index(Request r) {
      r.getResponse().redirect("/h3/index.html");
    }
  }

  /**
   * Resources of the classpath under a route of the app at the same path,
   * which keeps handling it.
   */
  @Test
  public void testServePathFromClasspath() throws Exception {
    createApp();
    new FlakResourceImpl(app).servePath("/h3",
                                        "/test-resources",
                                        Flak2CompatTest.class.getClassLoader(),
                                        false);
    app.scan(new Index());
    app.start();

    assertEquals("200 FOO", get("/h3/foo.html"));
    assertEquals("302 /h3/index.html", get("/h3"));
  }

  /**
   * servePath() serves a directory of the file system when given one.
   */
  @Test
  public void testServePathAndServeDirFromDirectory() throws Exception {
    Path dir = Files.createTempDirectory("flak");
    Files.writeString(dir.resolve("index.html"), "INDEX");
    createApp();
    new FlakResourceImpl(app).servePath("/a", dir.toString())
                             .serveDir("/b", dir.toFile());
    app.start();

    assertEquals("200 INDEX", get("/a/index.html"));
    assertEquals("200 INDEX", get("/b/"));
  }

  @Test
  public void testFormImpl() {
    flak.backend.jdk.FormImpl form = new flak.backend.jdk.FormImpl("a=x+y&b=%26", true);
    assertEquals("x y", form.get("a"));
    assertEquals("&", form.get("b"));
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
