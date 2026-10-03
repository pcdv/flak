package flask.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import flak.App;
import flak.AppFactory;
import flak.Request;
import flak.annotations.Route;
import flak.websocket.WebSocket;
import flak.websocket.WebSocketEndpoint;
import flask.test.util.RawWebSocketClient;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * flak-websocket, with the WebSocket client of the JDK, plus a raw one for
 * what the former would not send.
 */
public class WebSocketTest {

  /**
   * The connection-lost timeout of the tests that wait for the watchdog:
   * short, so that they do not wait for seconds, but long enough for an idle
   * client to answer the pings on a busy machine.
   */
  private static final Duration TIMEOUT = Duration.ofMillis(300);

  /**
   * The HttpClient of JDK 17 cannot be closed: its threads linger until it is
   * garbage collected, and it completes its futures in the common pool. Those
   * of the server must not linger.
   */
  @Rule
  public ThreadState.ThreadStateRule noZombies =
      new ThreadState.ThreadStateRule("HttpClient-.*", "ForkJoinPool.commonPool-.*");

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private App app;

  private final Recorder endpoint = new Recorder();

  @Route("/echo")
  public void echo(Request request) throws IOException {
    endpoint.accept(request);
  }

  @Route("/room/:name")
  public void room(String name, Request request) throws IOException {
    endpoint.accept(request, name);
  }

  @Route("/hello")
  public String hello() {
    return "hello";
  }

  @Before
  public void setUp() throws Exception {
    Assume.assumeTrue("websockets need the JDK backend",
                      System.getProperty("backend", "jdk").equals("jdk"));
    AppFactory factory = TestUtil.getFactory();
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
  public void echoesText() throws Exception {
    Client c = connect("/echo");
    assertEquals("open /echo null", endpoint.next());
    c.ws.sendText("héllo ✓", true).join();
    assertEquals("héllo ✓", c.next());
  }

  @Test
  public void echoesBinary() throws Exception {
    Client c = connect("/echo");
    c.ws.sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}), true).join();
    assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) c.next());
  }

  /**
   * Lengths on 7, 16 and 64 bits, both ways, and a message in fragments.
   */
  @Test
  public void echoesMessagesOfAnySize() throws Exception {
    Client c = connect("/echo");
    for (int size : new int[]{125, 126, 65535, 65536, 1_000_000}) {
      String text = "x".repeat(size);
      c.ws.sendText(text, true).join();
      assertEquals(text, c.next());
    }

    c.ws.sendText("frag", false).join();
    c.ws.sendText("mented", true).join();
    assertEquals("fragmented", c.next());
  }

  @Test
  public void attachesThePathVariable() throws Exception {
    Client c = connect("/room/lobby?nick=bob");
    assertEquals("open /room/lobby?nick=bob lobby", endpoint.next());
    c.ws.sendText("attachment", true).join();
    assertEquals("lobby", c.next());
  }

  @Test
  public void serverCloses() throws Exception {
    Client c = connect("/echo");
    c.ws.sendText("close:bye", true).join();
    assertEquals("close 4000 bye", c.next());
    endpoint.skip("open");
    assertEquals("close 4000 bye false", endpoint.next());
  }

  @Test
  public void clientCloses() throws Exception {
    Client c = connect("/echo");
    c.ws.sendClose(1000, "done").join();
    endpoint.skip("open");
    assertEquals("close 1000 done true", endpoint.next());
    // the echo of the server
    assertEquals("close 1000 ", c.next());
  }

  @Test
  public void answersPings() throws Exception {
    Client c = connect("/echo");
    c.ws.sendPing(ByteBuffer.wrap("hi".getBytes(UTF_8))).join();
    assertEquals("pong hi", c.next());
  }

  @Test
  public void broadcastsToEveryClient() throws Exception {
    Client c1 = connect("/echo");
    Client c2 = connect("/room/lobby");
    endpoint.skip("open");
    endpoint.skip("open");
    assertEquals(2, endpoint.getConnections().size());

    endpoint.broadcast("news");
    assertEquals("news", c1.next());
    assertEquals("news", c2.next());
  }

  @Test
  public void aFailingCallbackLeavesTheWebSocketOpen() throws Exception {
    Client c = connect("/echo");
    c.ws.sendText("throw", true).join();
    endpoint.skip("open");
    assertEquals("error boom", endpoint.next());
    c.ws.sendText("still there", true).join();
    assertEquals("still there", c.next());
  }

  @Test
  public void closesOnATooBigMessage() throws Exception {
    endpoint.setMaxMessageSize(10);
    Client c = connect("/echo");
    c.ws.sendText("0123456789", true).join();
    assertEquals("0123456789", c.next());

    c.ws.sendText("01234", false).join();
    c.ws.sendText("56789X", true).join();
    assertEquals("close 1009 Message bigger than 10 bytes", c.next());
  }

  @Test
  public void closesOnAnUnmaskedFrame() throws Exception {
    try (RawWebSocketClient c = rawClient()) {
      String head = c.handshake("/echo", "13");
      assertTrue(head, head.startsWith("HTTP/1.1 101 "));
      assertTrue(head, head.contains("Sec-WebSocket-Accept: " + RawWebSocketClient.ACCEPT));

      c.send(1, "masked", true);
      assertEquals("1 masked", c.read());

      c.send(1, "unmasked", false);
      assertEquals("8 1002 Unmasked frame", c.read());
      assertTrue(c.isAtEOF());
    }
    endpoint.skip("open");
    assertEquals("close 1002 Unmasked frame false", endpoint.next());
  }

  @Test
  public void refusesAnotherVersion() throws Exception {
    try (RawWebSocketClient c = rawClient()) {
      String head = c.handshake("/echo", "8");
      assertTrue(head, head.startsWith("HTTP/1.1 426 "));
      assertTrue(head, head.toLowerCase().contains("sec-websocket-version: 13"));
    }
  }

  @Test
  public void refusesAPlainRequest() throws Exception {
    HttpURLConnection con = (HttpURLConnection) new URL(app.getRootUrl() + "/echo").openConnection();
    assertEquals(400, con.getResponseCode());
  }

  /**
   * The server must be left in a state to serve other requests, on new
   * connections as on those it keeps alive.
   */
  @Test
  public void servesHttpAfterWebSockets() throws Exception {
    SimpleClient http = new SimpleClient(app.getRootUrl());
    assertEquals("hello", http.get("/hello"));
    for (int i = 0; i < 20; i++) {
      Client c = connect("/echo");
      c.ws.sendText("close:" + i, true).join();
      assertEquals("close 4000 " + i, c.next());
      assertEquals("hello", http.get("/hello"));
    }
  }

  /**
   * The server learns that the exchange of a websocket is over only when the
   * response flak tries to send after it fails: it must then forget the
   * connection, or every websocket would leak one until the server stops.
   */
  @Test
  public void releasesTheConnectionsOfHttpServer() throws Exception {
    Set<?> connections = connectionsOfHttpServer();
    for (int i = 0; i < 4; i++) {
      Client c = connect("/echo");
      endpoint.skip("open");
      assertEquals(1, connections.size());

      // closed by either side
      if (i % 2 == 0)
        c.ws.sendText("close:" + i, true).join();
      else
        c.ws.sendClose(1000, "").join();
      endpoint.skip("close");

      long deadline = System.currentTimeMillis() + 5000;
      while (!connections.isEmpty() && System.currentTimeMillis() < deadline)
        Thread.sleep(10);
      assertEquals(connections.toString(), 0, connections.size());
    }
  }

  /**
   * ServerImpl.allConnections, which holds every connection that HttpServer
   * has not closed yet.
   */
  private Set<?> connectionsOfHttpServer() throws ReflectiveOperationException {
    Object server = field(app.getServer(), "srv");
    Object impl = field(server, "server");
    return (Set<?>) field(impl, "allConnections");
  }

  private static Object field(Object obj, String name) throws ReflectiveOperationException {
    Field f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(obj);
  }

  @Test
  public void stoppingTheAppDropsTheWebSockets() throws Exception {
    Client c = connect("/echo");
    endpoint.skip("open");
    app.stop();
    app = null;

    assertEquals("close 1006  true", endpoint.next());
    // the client of JDK 17 reports an error, that of JDK 21 a closure
    String last = String.valueOf(c.next());
    assertTrue(last, last.startsWith("error") || last.startsWith("close 1006"));
    // and the thread that served it is gone, see noZombies
  }

  @Test
  public void keepsAnIdleClientThatAnswersPings() throws Exception {
    endpoint.setConnectionLostTimeout(TIMEOUT);
    Client c = connect("/echo");
    endpoint.skip("open");
    // the client of the JDK answers pings on its own
    Thread.sleep(TIMEOUT.multipliedBy(7).dividedBy(2).toMillis());
    c.ws.sendText("still there", true).join();
    assertEquals("still there", c.next());
    assertEquals(null, endpoint.events.poll());
  }

  /**
   * While a callback runs, nothing reads the frames of the client: its
   * answers to pings wait in the socket, and must not be taken for silence.
   */
  @Test
  public void keepsAClientWhileACallbackIsSlow() throws Exception {
    endpoint.setConnectionLostTimeout(TIMEOUT);
    Client c = connect("/echo");
    endpoint.skip("open");
    c.ws.sendText("sleep:" + TIMEOUT.multipliedBy(7).dividedBy(2).toMillis(), true).join();
    assertEquals("slept", c.next());
    c.ws.sendText("still there", true).join();
    assertEquals("still there", c.next());
    assertEquals(null, endpoint.events.poll());
  }

  @Test
  public void dropsAClientThatDoesNotAnswerPings() throws Exception {
    endpoint.setConnectionLostTimeout(TIMEOUT);
    try (RawWebSocketClient c = rawClient()) {
      c.handshake("/echo", "13");
      endpoint.skip("open");
      // read, not answered
      assertEquals("9 ", c.read());
      assertEquals("close 1006 Connection lost: no answer to pings false", endpoint.next());
    }
  }

  /**
   * A client that stops reading, but is still connected and talking: a send
   * to it blocks, until the watchdog drops it.
   */
  @Test(timeout = 20_000)
  public void dropsAClientThatStopsReading() throws Exception {
    endpoint.setConnectionLostTimeout(TIMEOUT);
    try (RawWebSocketClient c = rawClient()) {
      c.handshake("/echo", "13");
      endpoint.skip("open");
      WebSocket ws = endpoint.getConnections().iterator().next();

      Thread talker = new Thread(() -> {
        try {
          while (true) {
            c.send(1, "quiet:", true);
            Thread.sleep(20);
          }
        }
        catch (Exception e) {
          // dropped, or interrupted
        }
      });
      talker.start();

      try {
        byte[] big = new byte[1 << 20];
        for (int i = 0; i < 500; i++)
          ws.send(big);
        throw new AssertionError("500MiB sent to a client that does not read");
      }
      catch (UncheckedIOException expected) {
        assertEquals("close 1006 Connection lost: the client stopped reading false",
                     endpoint.next());
      }
      finally {
        talker.interrupt();
        talker.join();
      }
    }
  }

  @Test
  public void agreesOnTheFirstProtocolOfTheClient() throws Exception {
    endpoint.setProtocols("v2.chat", "v1.chat");
    Client client = new Client();
    URI uri = URI.create(app.getRootUrl().replaceFirst("^http", "ws") + "/echo");
    client.ws = HTTP.newWebSocketBuilder()
                    .subprotocols("v1.chat", "v2.chat")
                    .buildAsync(uri, client)
                    .get(5, TimeUnit.SECONDS);

    assertEquals("v1.chat", client.ws.getSubprotocol());
    client.ws.sendText("protocol", true).join();
    assertEquals("v1.chat", client.next());
  }

  @Test
  public void agreesOnNoProtocolWhenNoneIsInCommon() throws Exception {
    endpoint.setProtocols("v2.chat");
    try (RawWebSocketClient c = rawClient()) {
      String head = c.handshake("/echo", "13", "Sec-WebSocket-Protocol: v1.chat, v3.chat");
      assertTrue(head, head.startsWith("HTTP/1.1 101 "));
      assertTrue(head, !head.contains("Sec-WebSocket-Protocol"));
      c.send(1, "protocol", true);
      assertEquals("1 null", c.read());
    }
  }

  private Client connect(String path) throws Exception {
    Client client = new Client();
    URI uri = URI.create(app.getRootUrl().replaceFirst("^http", "ws") + path);
    client.ws = HTTP.newWebSocketBuilder()
                    .buildAsync(uri, client)
                    .get(5, TimeUnit.SECONDS);
    return client;
  }

  private RawWebSocketClient rawClient() throws IOException {
    Socket s = new Socket();
    // small, so that a client that does not read soon blocks the server
    s.setReceiveBufferSize(1024);
    s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), app.getServer().getPort()));
    s.setSoTimeout(5000);
    return new RawWebSocketClient(s);
  }

  /**
   * Echoes what it receives, or closes, or throws, as told by the message,
   * and records what happens.
   */
  static class Recorder extends WebSocketEndpoint {
    final BlockingQueue<String> events = new LinkedBlockingQueue<>();

    @Override
    public void onOpen(WebSocket conn, Request handshake) {
      events.add("open " + conn.getResourceDescriptor() + " " + conn.getAttachment());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      if (message.startsWith("close:"))
        conn.close(4000, message.substring(6));
      else if (message.equals("throw"))
        throw new IllegalStateException("boom");
      else if (message.equals("attachment"))
        conn.send(conn.<String>getAttachment());
      else if (message.equals("protocol"))
        conn.send(String.valueOf(conn.getProtocol()));
      else if (message.startsWith("quiet:"))
        return;
      else if (message.startsWith("sleep:")) {
        try {
          Thread.sleep(Long.parseLong(message.substring(6)));
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        conn.send("slept");
      }
      else
        conn.send(message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
      conn.send(message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      events.add("close " + code + " " + reason + " " + remote);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
      events.add("error " + ex.getMessage());
    }

    String next() throws InterruptedException {
      String event = events.poll(5, TimeUnit.SECONDS);
      assertNotNull("no event", event);
      return event;
    }

    void skip(String prefix) throws InterruptedException {
      String event = next();
      assertTrue(event, event.startsWith(prefix));
    }
  }

  /**
   * Records what the JDK client receives: whole messages, byte[] for binary
   * ones, and the pongs, closings and errors as strings.
   */
  static class Client implements java.net.http.WebSocket.Listener {
    final BlockingQueue<Object> received = new LinkedBlockingQueue<>();
    private final StringBuilder text = new StringBuilder();
    private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
    java.net.http.WebSocket ws;

    @Override
    public CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
      text.append(data);
      if (last) {
        received.add(text.toString());
        text.setLength(0);
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onBinary(java.net.http.WebSocket ws, ByteBuffer data, boolean last) {
      byte[] bytes = new byte[data.remaining()];
      data.get(bytes);
      binary.writeBytes(bytes);
      if (last) {
        received.add(binary.toByteArray());
        binary.reset();
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onPong(java.net.http.WebSocket ws, ByteBuffer message) {
      received.add("pong " + UTF_8.decode(message));
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(java.net.http.WebSocket ws, int code, String reason) {
      received.add("close " + code + " " + reason);
      return null;
    }

    @Override
    public void onError(java.net.http.WebSocket ws, Throwable error) {
      received.add("error " + error);
    }

    Object next() throws InterruptedException {
      Object msg = received.poll(5, TimeUnit.SECONDS);
      assertNotNull("nothing received", msg);
      return msg;
    }
  }
}
