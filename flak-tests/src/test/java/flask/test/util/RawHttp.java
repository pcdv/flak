package flask.test.util;

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
 * Sends requests over a bare socket, with headers that HttpURLConnection
 * refuses to send, such as Origin or Sec-Fetch-Site.
 */
public class RawHttp {

  public record Reply(int status, List<String> head, String body) {
    public String header(String name) {
      List<String> values = headers(name);
      return values.isEmpty() ? null : String.join(", ", values);
    }

    public List<String> headers(String name) {
      List<String> res = new ArrayList<>();
      for (String line : head) {
        int colon = line.indexOf(':');
        if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name))
          res.add(line.substring(colon + 1).trim());
      }
      return res;
    }
  }

  /**
   * Sends a request without a body, and reads the whole response, which the
   * server ends by closing the connection. The Host header is "localhost"
   * unless specified.
   */
  public static Reply send(String rootUrl, String requestLine, String... headers) throws IOException {
    URI root = URI.create(rootUrl);
    try (Socket s = new Socket(InetAddress.getLoopbackAddress(), root.getPort())) {
      s.setSoTimeout(5000);
      StringBuilder req = new StringBuilder(requestLine + " HTTP/1.1\r\n");
      if (List.of(headers).stream().noneMatch(h -> h.toLowerCase().startsWith("host:")))
        req.append("Host: localhost\r\n");
      req.append("Connection: close\r\n");
      for (String h : headers)
        req.append(h).append("\r\n");
      req.append("\r\n");
      OutputStream out = s.getOutputStream();
      out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
      out.flush();

      InputStream in = s.getInputStream();
      String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      int end = response.indexOf("\r\n\r\n");
      List<String> head = List.of(response.substring(0, end).split("\r\n"));
      String body = response.substring(end + 4);
      if (head.stream().anyMatch(l -> l.toLowerCase().startsWith("transfer-encoding: chunked")))
        body = unchunk(body);
      return new Reply(Integer.parseInt(head.get(0).split(" ")[1]), head.subList(1, head.size()), body);
    }
  }

  private static String unchunk(String body) {
    StringBuilder res = new StringBuilder();
    int i = 0;
    while (true) {
      int eol = body.indexOf("\r\n", i);
      int len = Integer.parseInt(body.substring(i, eol).trim(), 16);
      if (len == 0)
        return res.toString();
      res.append(body, eol + 2, eol + 2 + len);
      i = eol + 2 + len + 2;
    }
  }
}
