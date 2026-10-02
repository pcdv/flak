package flak.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import flak.App;
import flak.HttpException;
import flak.Request;
import flak.backend.jdk.JdkRequest;
import flak.spi.util.Log;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Serves websockets on the routes of a flak app, with the JDK backend.
 * Modelled on the WebSocketServer of Java-WebSocket (org.java_websocket):
 * override the callbacks, then hand the requests of a route over to
 * {@link #accept(Request)}.
 * <pre>
 * WebSocketEndpoint chat = new WebSocketEndpoint() {
 *   public void onMessage(WebSocket conn, String message) {
 *     broadcast(message);
 *   }
 * };
 *
 * &#64;Route("/chat")
 * public void chat(Request request) throws IOException {
 *   chat.accept(request);
 * }
 * </pre>
 * Being an ordinary route, the handshake goes through the hooks and the
 * plugins of the app, e.g. the login checks of flak-login, and the handler can
 * refuse it by throwing an {@link HttpException}.
 * <p>
 * The callbacks of a websocket are called by the thread that serves its
 * route, never concurrently. That thread is busy for as long as the websocket
 * is open.
 * <p>
 * While websockets are open, a watchdog pings the idle ones, and drops those
 * that look dead, see {@link #setConnectionLostTimeout(int)}.
 *
 * @author pcdv
 */
public abstract class WebSocketEndpoint {

  /**
   * Same as the body size limit of an app.
   */
  public static final long DEFAULT_MAX_MESSAGE_SIZE = App.DEFAULT_MAX_BODY_SIZE;

  /**
   * In seconds, as in Java-WebSocket.
   */
  public static final int DEFAULT_CONNECTION_LOST_TIMEOUT = 60;

  private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

  private final Set<Connection> connections = ConcurrentHashMap.newKeySet();

  private volatile long maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;

  private volatile List<String> protocols = List.of();

  /**
   * In seconds. Guarded by connections, like the watchdog.
   */
  private int connectionLostTimeout = DEFAULT_CONNECTION_LOST_TIMEOUT;

  /**
   * Runs while websockets are open, so that an endpoint without any costs no
   * thread.
   */
  private ScheduledThreadPoolExecutor watchdog;

  /**
   * Same as {@link #accept(Request, Object)}, with no attachment.
   */
  public void accept(Request request) throws IOException {
    accept(request, null);
  }

  /**
   * Upgrades the request to a websocket and serves it: returns only once the
   * websocket is closed, after {@link #onClose}. Call it from the handler of
   * a GET route, before anything is written to the response.
   *
   * @param request the request of the route handler
   * @param attachment attached to the websocket before {@link #onOpen} is
   * called, e.g. the session or a path variable of the route
   * @throws HttpException 400 if the request is not a websocket handshake,
   * 426 if it asks for another version of the protocol than 13
   * @throws IllegalStateException if the app does not run on the JDK backend
   */
  public void accept(Request request, Object attachment) throws IOException {
    if (!(request instanceof JdkRequest))
      throw new IllegalStateException(
        "flak-websocket requires flak-backend-jdk, not " + request.getClass().getName());

    HttpExchange exchange = ((JdkRequest) request).getExchange();
    if (exchange.getResponseCode() != -1)
      throw new IllegalStateException("A response was already sent");

    String acceptKey = acceptKey(request);
    Connection ws = new Connection(this,
                                   exchange,
                                   RawStreams.of(exchange),
                                   maxMessageSize,
                                   selectProtocol(exchange.getRequestHeaders()
                                                          .get("Sec-WebSocket-Protocol")),
                                   attachment);
    ws.sendHandshake(acceptKey);

    added(ws);
    try {
      ws.open(request);
      ws.run();
    }
    finally {
      removed(ws);
    }
    ws.notifyClose();

    // NB: the route returns normally, and flak then tries to send a response
    // on the connection, which is closed by now. That IOException is what
    // makes HttpServer drop the connection from its bookkeeping
  }

  /**
   * Checks the handshake (RFC 6455, section 4.2.1) and returns the value of
   * Sec-WebSocket-Accept.
   */
  private static String acceptKey(Request r) {
    if (!"GET".equals(r.getMethod())
        || !hasToken(r.getHeader("Upgrade"), "websocket")
        || !hasToken(r.getHeader("Connection"), "upgrade"))
      throw new HttpException(400, "Expected a websocket handshake");

    String version = r.getHeader("Sec-WebSocket-Version");
    if (version == null || !version.trim().equals("13")) {
      r.getResponse().addHeader("Sec-WebSocket-Version", "13");
      throw new HttpException(426, "Unsupported websocket version: " + version);
    }

    String key = r.getHeader("Sec-WebSocket-Key");
    if (key == null || !isNonce(key.trim()))
      throw new HttpException(400, "Invalid Sec-WebSocket-Key: " + key);

    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] digest = sha1.digest((key.trim() + GUID).getBytes(US_ASCII));
      return Base64.getEncoder().encodeToString(digest);
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Picks the first subprotocol of the client that the endpoint supports.
   * With none in common, the handshake goes on without one: it is up to the
   * client to give up, as RFC 6455 has it.
   *
   * @param offered the values of Sec-WebSocket-Protocol, which may each hold
   * several protocols, in the order of preference of the client
   */
  private String selectProtocol(List<String> offered) {
    List<String> supported = protocols;
    if (offered != null)
      for (String header : offered)
        for (String p : header.split(","))
          if (supported.contains(p.trim()))
            return p.trim();
    return null;
  }

  /**
   * Tells whether a comma separated header holds specified token, e.g.
   * "keep-alive, Upgrade" holds "upgrade".
   */
  private static boolean hasToken(String header, String token) {
    if (header != null)
      for (String t : header.split(","))
        if (t.trim().equalsIgnoreCase(token))
          return true;
    return false;
  }

  /**
   * The key of the client is 16 random bytes, encoded in base64.
   */
  private static boolean isNonce(String key) {
    try {
      return Base64.getDecoder().decode(key).length == 16;
    }
    catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Returns the websockets that are currently open or closing.
   */
  public Collection<WebSocket> getConnections() {
    return Collections.unmodifiableList(new ArrayList<>(connections));
  }

  /**
   * Sends a text message to all the open websockets, skipping those that
   * close or fail in the meantime.
   */
  public void broadcast(String text) {
    byte[] utf8 = text.getBytes(UTF_8);
    for (Connection c : connections) {
      try {
        c.sendText(utf8);
      }
      catch (RuntimeException ignored) {
        // closing, or broken and dropped: its onClose() tells the rest
      }
    }
  }

  /**
   * Sends a binary message to all the open websockets, skipping those that
   * close or fail in the meantime.
   */
  public void broadcast(byte[] data) {
    for (Connection c : connections) {
      try {
        c.send(data);
      }
      catch (RuntimeException ignored) {
        // closing, or broken and dropped: its onClose() tells the rest
      }
    }
  }

  /**
   * Sets the maximum size of a message received, fragments included: a
   * bigger one closes the websocket with {@link CloseFrame#TOOBIG}. Negative
   * means no limit. Applies to the websockets accepted from now on.
   * <p>
   * Defaults to {@link #DEFAULT_MAX_MESSAGE_SIZE}.
   */
  public void setMaxMessageSize(long maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public long getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Sets the subprotocols this endpoint speaks, e.g. "graphql-transport-ws".
   * The handshake agrees on the first one the client offers among them, see
   * {@link WebSocket#getProtocol()}. A client that offers none is accepted
   * all the same, without a subprotocol. Applies to the websockets accepted
   * from now on.
   */
  public void setProtocols(String... protocols) {
    this.protocols = List.copyOf(Arrays.asList(protocols));
  }

  public List<String> getProtocols() {
    return protocols;
  }

  /**
   * Sets how long a websocket may go without a sign of life before it is
   * dropped, as in Java-WebSocket. A websocket from which nothing is received
   * for half that time is pinged, and one that has not answered after one and
   * a half times that time is dropped, as is one that has not taken anything
   * it is sent for that time. Dropping it closes it with
   * {@link CloseFrame#ABNORMAL_CLOSE}, and frees a thread that a send to it
   * would have blocked, e.g. in {@link #broadcast(String)}.
   * <p>
   * Without it, a client that vanishes without closing its connection, e.g.
   * a laptop going to sleep, keeps the thread of its websocket until the
   * operating system gives up on the connection, which may take hours.
   *
   * @param seconds the timeout, 0 to disable the watchdog
   */
  public void setConnectionLostTimeout(int seconds) {
    synchronized (connections) {
      connectionLostTimeout = seconds;
      stopWatchdog();
      if (!connections.isEmpty())
        startWatchdog();
    }
  }

  public int getConnectionLostTimeout() {
    synchronized (connections) {
      return connectionLostTimeout;
    }
  }

  private void added(Connection ws) {
    synchronized (connections) {
      connections.add(ws);
      if (watchdog == null)
        startWatchdog();
    }
  }

  private void removed(Connection ws) {
    synchronized (connections) {
      connections.remove(ws);
      if (connections.isEmpty())
        stopWatchdog();
    }
  }

  /**
   * Two threads: a ping to a client that stopped reading may block, but not
   * the check that drops that client, and unblocks the ping.
   */
  private void startWatchdog() {
    if (connectionLostTimeout <= 0)
      return;

    long timeout = TimeUnit.SECONDS.toNanos(connectionLostTimeout);
    watchdog = new ScheduledThreadPoolExecutor(2, r -> {
      Thread t = new Thread(r, "websocket-watchdog");
      t.setDaemon(true);
      return t;
    });
    watchdog.scheduleAtFixedRate(() -> forEach(c -> c.checkAlive(timeout)),
                                 timeout / 4, timeout / 4, TimeUnit.NANOSECONDS);
    watchdog.scheduleAtFixedRate(() -> forEach(c -> c.pingIfIdle(timeout / 2)),
                                 timeout / 2, timeout / 2, TimeUnit.NANOSECONDS);
  }

  /**
   * Does not interrupt the watchdog, which would close the channel of a
   * websocket it is pinging.
   */
  private void stopWatchdog() {
    if (watchdog != null) {
      watchdog.shutdown();
      watchdog = null;
    }
  }

  /**
   * Applies a task of the watchdog to every websocket. A task that throws
   * would never run again.
   */
  private void forEach(Consumer<Connection> task) {
    for (Connection c : connections) {
      try {
        task.accept(c);
      }
      catch (RuntimeException e) {
        Log.error(e, e);
      }
    }
  }

  // /////////// Callbacks

  /**
   * Called once the handshake is accepted, before any message is received.
   *
   * @param handshake the request of the route handler, e.g. to read its
   * headers or cookies
   */
  public void onOpen(WebSocket conn, Request handshake) {
  }

  public void onMessage(WebSocket conn, String message) {
  }

  /**
   * Called for a binary message. The buffer wraps an array that belongs to
   * the callee.
   */
  public void onMessage(WebSocket conn, ByteBuffer message) {
  }

  /**
   * Called once the websocket is closed, whether by a closing handshake or
   * by the connection dropping.
   *
   * @param code the code of the side that started the closing handshake, or
   * {@link CloseFrame#ABNORMAL_CLOSE} when the connection dropped
   * @param remote true unless the closure was started by the server, i.e. by
   * {@link WebSocket#close} or by a violation of the protocol by the client
   */
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
  }

  /**
   * Called when another callback throws. The websocket stays open.
   */
  public void onError(WebSocket conn, Exception ex) {
    Log.error("Error on " + conn, ex);
  }
}
