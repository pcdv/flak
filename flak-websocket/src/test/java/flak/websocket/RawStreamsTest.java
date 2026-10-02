package flak.websocket;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The tests of this module run without --add-opens, so that the connection is
 * reached with Unsafe: flak-tests runs the rest with the flag.
 */
public class RawStreamsTest {

  @Test
  public void reachesTheConnectionWithoutAddOpens() throws Exception {
    assertFalse("sun.net.httpserver must not be open for this test",
                HttpServer.class.getModule()
                                .isOpen("sun.net.httpserver", getClass().getModule()));

    InetAddress loopback = InetAddress.getLoopbackAddress();
    HttpServer srv = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    srv.createContext("/", exchange -> {
      // a response HttpServer knows nothing about
      OutputStream out = RawStreams.of(exchange).out;
      out.write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nraw".getBytes(US_ASCII));
      out.flush();
      // closes the connection, since no response was started
      exchange.close();
    });
    srv.start();

    try (Socket s = new Socket(loopback, srv.getAddress().getPort())) {
      s.getOutputStream().write("GET / HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(US_ASCII));
      assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nraw",
                   new String(s.getInputStream().readAllBytes(), US_ASCII));
    }
    finally {
      srv.stop(0);
    }
  }
}
