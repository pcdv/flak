package flask.test.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a Server-Sent Events stream over a bare socket, as it arrives, and
 * can drop the connection at any time, like a browser closing its tab.
 */
public class SseClient implements Closeable {

  private final Socket socket;

  private final InputStream in;

  private final List<String> head = new ArrayList<>();

  private final boolean chunked;

  private int chunkLeft;

  private boolean ended;

  public SseClient(String rootUrl, String path, String... headers) throws IOException {
    URI root = URI.create(rootUrl);
    socket = new Socket(InetAddress.getLoopbackAddress(), root.getPort());
    socket.setSoTimeout(5000);
    String base = root.getPath().replaceAll("/$", "");
    StringBuilder req = new StringBuilder("GET " + base + path + " HTTP/1.1\r\n");
    req.append("Host: localhost\r\n");
    for (String h : headers)
      req.append(h).append("\r\n");
    req.append("\r\n");
    OutputStream out = socket.getOutputStream();
    out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
    out.flush();

    in = socket.getInputStream();
    for (String line = rawLine(); !line.isEmpty(); line = rawLine())
      head.add(line);
    chunked = header("Transfer-Encoding") != null
              && header("Transfer-Encoding").equalsIgnoreCase("chunked");
  }

  public int status() {
    return Integer.parseInt(head.get(0).split(" ")[1]);
  }

  public String header(String name) {
    for (String line : head) {
      int colon = line.indexOf(':');
      if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name))
        return line.substring(colon + 1).trim();
    }
    return null;
  }

  /**
   * Returns the lines of the next event, comments left out, or null at the
   * end of the response.
   */
  public List<String> nextEvent() throws IOException {
    List<String> lines = new ArrayList<>();
    while (true) {
      String line = bodyLine();
      if (line == null)
        return null;
      if (line.isEmpty()) {
        if (!lines.isEmpty())
          return lines;
      }
      else if (!line.startsWith(":"))
        lines.add(line);
    }
  }

  /**
   * Reads the rest of the body, which ends when the server ends the response.
   */
  public String readToEnd() throws IOException {
    ByteArrayOutputStream res = new ByteArrayOutputStream();
    for (int b = bodyByte(); b >= 0; b = bodyByte())
      res.write(b);
    return res.toString(StandardCharsets.UTF_8);
  }

  private String bodyLine() throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    while (true) {
      int b = bodyByte();
      if (b < 0)
        return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
      if (b == '\n')
        return line.toString(StandardCharsets.UTF_8);
      line.write(b);
    }
  }

  private int bodyByte() throws IOException {
    if (!chunked)
      return in.read();
    if (ended)
      return -1;
    if (chunkLeft == 0) {
      String size = rawLine();
      chunkLeft = Integer.parseInt(size.trim(), 16);
      if (chunkLeft == 0) {
        ended = true;
        return -1;
      }
    }
    int b = in.read();
    if (b < 0)
      throw new IOException("Truncated chunk");
    if (--chunkLeft == 0)
      rawLine();
    return b;
  }

  private String rawLine() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int b = in.read(); b != '\n'; b = in.read()) {
      if (b < 0)
        throw new IOException("Unexpected end of stream");
      if (b != '\r')
        sb.append((char) b);
    }
    return sb.toString();
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
