package flask.test;

import flak.Request;
import flak.annotations.MaxBodySize;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * A handler that leaves most of the request body unread must not break the
 * connection: the server reads the rest itself, so the client gets the whole
 * response and can reuse the connection.
 */
public class UnreadBodyTest extends AbstractAppTest {

  /** More than the 64 KiB that the JDK server drains by default. */
  private static final int BODY_SIZE = 1024 * 1024;

  /** The netty backend closes the connection after each response. */
  private static final boolean KEEP_ALIVE =
    System.getProperty("backend", "jdk").equals("jdk");

  @Route("/partial")
  @Post
  @MaxBodySize(2 * BODY_SIZE)
  public String partial(Request r) throws IOException {
    r.getInputStream().read();
    return "ok";
  }

  @Route("/fail")
  @Post
  @MaxBodySize(2 * BODY_SIZE)
  public String fail() {
    throw new IllegalStateException("fails before reading the body");
  }

  @Test
  public void connectionSurvivesPartiallyReadBody() throws Exception {
    assertConnectionSurvives("/partial", 200);
  }

  @Test
  public void connectionSurvivesUnreadBody() throws Exception {
    assertConnectionSurvives("/fail", 500);
  }

  private void assertConnectionSurvives(String path, int status) throws Exception {
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(),
                                    app.getServer().getPort())) {
      socket.setSoTimeout(10_000);
      OutputStream out = socket.getOutputStream();
      InputStream in = new BufferedInputStream(socket.getInputStream());

      post(out, path, new byte[BODY_SIZE]);
      assertEquals(status, readResponse(in));

      if (KEEP_ALIVE) {
        // fails if the server closed the connection
        post(out, path, new byte[BODY_SIZE]);
        assertEquals(status, readResponse(in));
      }
    }
  }

  private static void post(OutputStream out, String path, byte[] body) throws IOException {
    String head = "POST " + path + " HTTP/1.1\r\n"
                  + "Host: localhost\r\n"
                  + "Content-Length: " + body.length + "\r\n"
                  + "\r\n";
    out.write(head.getBytes(StandardCharsets.US_ASCII));
    out.write(body);
    out.flush();
  }

  /** Reads one HTTP/1.1 response, chunked or with a content length, and returns its status. */
  private int readResponse(InputStream in) throws IOException {
    String statusLine = readLine(in);
    boolean chunked = false;
    int length = 0;
    for (String header = readLine(in); !header.isEmpty(); header = readLine(in)) {
      String lower = header.toLowerCase(Locale.ROOT);
      if (lower.startsWith("transfer-encoding:") && lower.contains("chunked"))
        chunked = true;
      else if (lower.startsWith("content-length:"))
        length = Integer.parseInt(header.substring("content-length:".length()).trim());
    }
    if (chunked) {
      for (int size = Integer.parseInt(readLine(in).trim(), 16); size > 0;
           size = Integer.parseInt(readLine(in).trim(), 16)) {
        in.readNBytes(size);
        readLine(in);
      }
      // the empty line that ends the chunked body
      readLine(in);
    }
    else {
      in.readNBytes(length);
    }
    return Integer.parseInt(statusLine.split(" ")[1]);
  }

  private static String readLine(InputStream in) throws IOException {
    StringBuilder line = new StringBuilder();
    for (int c = in.read(); c != '\n'; c = in.read()) {
      if (c < 0)
        throw new EOFException("Connection closed, line read so far: " + line);
      if (c != '\r')
        line.append((char) c);
    }
    return line.toString();
  }
}
