package flask.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import flak.App;
import flak.AppFactory;
import flak.Request;
import flak.annotations.Route;
import flak.websocket.WebSocket;
import flak.websocket.WebSocketEndpoint;
import flask.test.util.RawWebSocketClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * flak-websocket on an HttpsServer: the streams it takes over must be those
 * of the TLS session, not the bare socket.
 */
public class WebSocketSSLTest {

  private App app;

  private SSLContext context;

  private final BlockingQueue<String> closed = new LinkedBlockingQueue<>();

  private final BlockingQueue<WebSocket> opened = new LinkedBlockingQueue<>();

  private final WebSocketEndpoint echo = new WebSocketEndpoint() {
    @Override
    public void onOpen(WebSocket conn, Request handshake) {
      opened.add(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      if (!message.equals("quiet"))
        conn.send(message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      closed.add(code + " " + reason);
    }
  };

  @Route("/echo")
  public void echo(Request request) throws IOException {
    echo.accept(request);
  }

  @Before
  public void setUp() throws Exception {
    Assume.assumeTrue("websockets need the JDK backend",
                      System.getProperty("backend", "jdk").equals("jdk"));
    // trusts the certificate it serves, which is all the client needs
    context = SSLTest.getSslContext("/test-resources/lig.keystore", "foobar");

    AppFactory factory = TestUtil.getFactory();
    factory.getServer().setSSLContext(context);
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    app = factory.createApp();
    app.scan(this);
    app.start();
  }

  @After
  public void tearDown() {
    if (app != null)
      app.stop();
  }

  @Test
  public void echoesOverTls() throws Exception {
    try (RawWebSocketClient c = connect()) {
      String head = c.handshake("/echo", "13");
      assertTrue(head, head.startsWith("HTTP/1.1 101 "));

      c.send(1, "secure", true);
      assertEquals("1 secure", c.read());

      c.send(8, "", true);
      assertEquals("8 ", c.read());
    }
  }

  /**
   * Dropping a client that stopped reading must not wait for the TLS session
   * to be closed properly, which would block like the send it unblocks.
   */
  @Test(timeout = 20_000)
  public void dropsAClientThatStopsReading() throws Exception {
    // not the 300ms of WebSocketTest, which a slow CI runner once exceeded:
    // the websocket was dropped before the sends were stuck. A stuck send
    // does not keep the server from reading over TLS, SSLStreams locking
    // reads and writes separately, so presumably the runner stalled
    echo.setConnectionLostTimeout(1);
    try (RawWebSocketClient c = connect()) {
      c.handshake("/echo", "13");
      Thread talker = new Thread(() -> {
        try {
          while (true) {
            c.send(1, "quiet", true);
            Thread.sleep(20);
          }
        }
        catch (Exception e) {
          // dropped, or interrupted
        }
      });
      talker.start();

      try {
        // the client reads the handshake before the endpoint lists the
        // websocket, which it does by the time it is opened
        WebSocket ws = opened.poll(5, TimeUnit.SECONDS);
        byte[] big = new byte[1 << 20];
        for (int i = 0; i < 500; i++)
          ws.send(big);
        throw new AssertionError("500MiB sent to a client that does not read");
      }
      catch (UncheckedIOException expected) {
        assertEquals("1006 Connection lost: the client stopped reading",
                     closed.poll(5, TimeUnit.SECONDS));
      }
      finally {
        talker.interrupt();
        talker.join();
      }
    }
  }

  private RawWebSocketClient connect() throws IOException {
    Socket s = context.getSocketFactory().createSocket();
    s.setReceiveBufferSize(1024);
    s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), app.getServer().getPort()));
    s.setSoTimeout(5000);
    return new RawWebSocketClient(s);
  }
}
