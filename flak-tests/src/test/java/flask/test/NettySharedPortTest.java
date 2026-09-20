package flask.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import flak.App;
import flak.annotations.Route;
import flak.backend.netty.NettyAppFactory;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Demonstrates an application that runs its own netty server: flak serves the
 * HTTP routes, the application serves websockets with its own netty handlers,
 * and both share a single port.
 * <p>
 * Flak binds nothing here. The application builds the pipeline and adds flak's
 * handler to it.
 */
public class NettySharedPortTest {

  /**
   * Nothing must be left running: neither the executor flak allocates for its
   * route handlers, nor the event loops of the server we own here.
   */
  @Rule
  public ThreadState.ThreadStateRule noZombies =
      new ThreadState.ThreadStateRule("globalEventExecutor-.*");

  private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

  private EventLoopGroup boss;
  private EventLoopGroup workers;
  private Channel channel;
  private App app;
  private NettyAppFactory factory;

  @Route("/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }

  @Before
  public void setUp() throws Exception {
    // flak is told it does not own a server: no socket, no event loop of its
    // own. The address is unknown for now, we bind on port 0 below
    factory = NettyAppFactory.attached(null, false);
    app = factory.createApp();
    app.scan(this);

    boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    workers = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

    ServerBootstrap b = new ServerBootstrap();
    b.group(boss, workers)
     .channel(NioServerSocketChannel.class)
     .childHandler(new ChannelInitializer<SocketChannel>() {
       @Override
       protected void initChannel(SocketChannel ch) {
         // netty handles the upgrade on /ws and passes every other request
         // down the pipeline, where flak serves its routes
         ch.pipeline()
           .addLast(new HttpServerCodec())
           .addLast(new HttpObjectAggregator(65536))
           .addLast(new WebSocketServerProtocolHandler("/ws"))
           .addLast(new EchoFrames())
           .addLast("flak", factory.getServer().getHttpHandler());
       }
     });

    channel = b.bind(0).sync().channel();

    // now that the port is known, tell flak what to advertise in getRootUrl()
    factory.setLocalAddress((InetSocketAddress) channel.localAddress());
    app.start();
  }

  @After
  public void tearDown() throws Exception {
    if (app != null)
      app.stop();
    if (channel != null)
      channel.close().await(5, TimeUnit.SECONDS);
    if (boss != null)
      boss.shutdownGracefully(0, 10, TimeUnit.MILLISECONDS).await(5, TimeUnit.SECONDS);
    if (workers != null)
      workers.shutdownGracefully(0, 10, TimeUnit.MILLISECONDS).await(5, TimeUnit.SECONDS);
  }

  private int port() {
    return ((InetSocketAddress) channel.localAddress()).getPort();
  }

  @Test
  public void flakServesItsRoutes() throws Exception {
    SimpleClient client = new SimpleClient("http://localhost:" + port());
    assertEquals("Hello world", client.get("/hello/world"));
  }

  @Test
  public void websocketIsServedOnTheSamePort() throws Exception {
    assertEquals("echo:ping", websocketEcho("/ws", "ping"));
  }

  /**
   * The point of the whole exercise: one port, both protocols, in any order.
   */
  @Test
  public void bothOnOnePort() throws Exception {
    SimpleClient client = new SimpleClient("http://localhost:" + port());
    assertEquals("Hello netty", client.get("/hello/netty"));
    assertEquals("echo:still here", websocketEcho("/ws", "still here"));
    assertEquals("Hello again", client.get("/hello/again"));
  }

  @Test
  public void rootUrlReportsTheAddressOfTheApplicationServer() {
    assertEquals("http://localhost:" + port(), app.getRootUrl());
  }

  private static class EchoFrames extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
      ctx.writeAndFlush(new TextWebSocketFrame("echo:" + frame.text()));
    }
  }

  /**
   * A websocket client written by hand: it keeps the test free of any client
   * library, and of the threads one would have to start and then wait for.
   */
  private String websocketEcho(String path, String message) throws Exception {
    try (Socket s = new Socket("localhost", port())) {
      // never block the build: a missing handshake or frame must fail, not hang
      s.setSoTimeout(5000);

      String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(US_ASCII));

      OutputStream out = s.getOutputStream();
      out.write(("GET " + path + " HTTP/1.1\r\n"
                 + "Host: localhost:" + port() + "\r\n"
                 + "Upgrade: websocket\r\n"
                 + "Connection: Upgrade\r\n"
                 + "Sec-WebSocket-Key: " + key + "\r\n"
                 + "Sec-WebSocket-Version: 13\r\n"
                 + "\r\n").getBytes(US_ASCII));
      out.flush();

      InputStream in = s.getInputStream();
      String status = readLine(in);
      assertTrue(status, status.startsWith("HTTP/1.1 101"));

      String accept = null;
      for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
        if (line.toLowerCase().startsWith("sec-websocket-accept:"))
          accept = line.substring(line.indexOf(':') + 1).trim();
      }
      assertEquals("handshake not accepted", expectedAccept(key), accept);

      writeMaskedTextFrame(out, message);
      return readTextFrame(in);
    }
  }

  private static String expectedAccept(String key) throws Exception {
    byte[] sha1 = MessageDigest.getInstance("SHA-1")
                               .digest((key + WS_GUID).getBytes(US_ASCII));
    return Base64.getEncoder().encodeToString(sha1);
  }

  private static void writeMaskedTextFrame(OutputStream out, String text) throws IOException {
    byte[] payload = text.getBytes(UTF_8);
    if (payload.length > 125)
      throw new IllegalArgumentException("short frames only");

    byte[] mask = { 0x1b, 0x2c, 0x3d, 0x4e };
    out.write(0x81);                    // FIN + text frame
    out.write(0x80 | payload.length);   // a client must mask its payload
    out.write(mask);
    for (int i = 0; i < payload.length; i++)
      out.write(payload[i] ^ mask[i % 4]);
    out.flush();
  }

  private static String readTextFrame(InputStream in) throws IOException {
    int first = in.read();
    if (first != 0x81)
      throw new IOException("Expected a final text frame, got " + first);

    int len = in.read() & 0x7f;          // a server does not mask
    if (len > 125)
      throw new IOException("short frames only, got length " + len);

    byte[] payload = new byte[len];
    for (int read = 0; read < len; ) {
      int n = in.read(payload, read, len - read);
      if (n < 0)
        throw new IOException("Truncated frame");
      read += n;
    }
    return new String(payload, UTF_8);
  }

  private static String readLine(InputStream in) throws IOException {
    StringBuilder s = new StringBuilder(80);
    for (int c = in.read(); c != -1; c = in.read()) {
      if (c == '\n')
        break;
      if (c != '\r')
        s.append((char) c);
    }
    return s.toString();
  }
}
