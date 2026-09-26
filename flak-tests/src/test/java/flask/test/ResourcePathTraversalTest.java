package flask.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks that resources cannot be served from outside the directory, or the
 * classpath folder, they are served from.
 * <p>
 * The requests are sent through a raw socket: HTTP clients normalize the path
 * of a URL before sending it, removing the very ".." we want to send.
 */
public class ResourcePathTraversalTest extends AbstractAppTest {

  private static final String SECRET = "SECRET-CONTENT";

  @Test
  public void testServeDir() throws Exception {
    Path base = Files.createTempDirectory("flak");
    Path root = Files.createDirectories(base.resolve("public"));
    Files.write(root.resolve("ok.txt"), "PUBLIC".getBytes());
    Path secret = base.resolve("secret.txt");
    Files.write(secret, SECRET.getBytes());

    app.serveDir("/static", root.toFile());

    assertTrue(get("/static/ok.txt").contains("PUBLIC"));

    assertNotFound("/static/../secret.txt");
    assertNotFound("/static/%2e%2e/secret.txt");
    assertNotFound("/static/..%2fsecret.txt");
    assertNotFound("/static/..%5csecret.txt");
    // an absolute path would make Path.resolve() ignore the root altogether:
    // "/static//tmp/..." on unix, "/static/C:/..." on windows
    assertNotFound("/static/" + secret.toAbsolutePath().toString().replace('\\', '/'));
    assertNotFound("/static/missing.txt");
  }

  @Test
  public void testServePathFromClasspath() throws Exception {
    app.serveClasspath("/static", "/test-resources");

    assertTrue(get("/static/foo.html").contains("FOO"));

    // any other resource of the classpath, a class file for instance
    assertNotFound("/static/../flask/test/TestUtil.class");
    assertNotFound("/static/%2e%2e/flask/test/TestUtil.class");
  }

  private void assertNotFound(String path) throws IOException {
    String response = get(path);
    assertFalse("Leaked through " + path, response.contains(SECRET));
    assertEquals("Status for " + path,
                 "HTTP/1.1 404 Not Found",
                 response.lines().findFirst().orElse(""));
  }

  private String get(String path) throws IOException {
    try (Socket s = new Socket(InetAddress.getLoopbackAddress(), app.getServer().getPort())) {
      OutputStream out = s.getOutputStream();
      out.write(("GET " + path + " HTTP/1.1\r\n" +
                 "Host: localhost\r\n" +
                 "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      out.flush();
      ByteArrayOutputStream res = new ByteArrayOutputStream();
      s.getInputStream().transferTo(res);
      return res.toString(StandardCharsets.ISO_8859_1);
    }
  }
}
