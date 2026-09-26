package flak.backend.netty;

import flak.WebServer;
import flak.spi.util.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyWebServer implements WebServer {

  private final Vector<NettyApp> apps = new Vector<>();
  private String hostName = "localhost";
  private InetSocketAddress address = new InetSocketAddress(0);
  private Channel channel;
  private static final long SHUTDOWN_TIMEOUT_MS = 5000;

  private SSLContext sslContext;
  private ExecutorService executor;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private boolean started;

  /**
   * True when the application runs its own netty server and flak only
   * contributes a handler to its pipeline: there is then no socket, no
   * bootstrap and no event loop of ours to manage.
   */
  private final boolean attached;

  private final boolean secure;

  private ChannelHandler httpHandler;

  public NettyWebServer() {
    this(false, null, false);
  }

  NettyWebServer(boolean attached, InetSocketAddress address, boolean secure) {
    this.attached = attached;
    this.secure = secure;
    if (address != null)
      this.address = address;
  }

  public boolean isAttached() {
    return attached;
  }

  /**
   * Flak's HTTP dispatch, as a plain netty handler, so that an application
   * owning its own server can add it to the pipeline it builds, e.g.
   * <pre>
   * ch.pipeline()
   *   .addLast(new HttpServerCodec())
   *   .addLast(new HttpObjectAggregator(65536))
   *   .addLast("flak", server.getHttpHandler());
   * </pre>
   * The handler is sharable: the same instance serves every channel.
   * <p>
   * It expects to receive aggregated requests, so a HttpObjectAggregator must
   * come before it in the pipeline.
   */
  public synchronized ChannelHandler getHttpHandler() {
    if (httpHandler == null)
      httpHandler = new NettyFlakHandler(this);
    return httpHandler;
  }

  @Override
  public void setSSLContext(SSLContext sslContext) {
    if (attached)
      throw new IllegalStateException(
        "This server is attached to a netty server owned by the application: " +
        "TLS belongs to the pipeline it builds, not to flak");
    if (started)
      throw new IllegalStateException("Server already started");
    this.sslContext = sslContext;
  }

  public SSLContext getSSLContext() {
    return sslContext;
  }

  public void addApp(NettyApp app) {
    apps.add(app);
  }

  /**
   * Returns the app that serves specified path, i.e. the one with the longest
   * root path the request path starts with, or null when there is none.
   */
  public NettyApp getApp(String path) {
    NettyApp res = null;
    for (NettyApp app : apps) {
      String root = app.getPath();
      if (path.equals(root) || path.startsWith(root.isEmpty() ? "/" : root + "/")) {
        if (res == null || root.length() > res.getPath().length())
          res = app;
      }
    }
    return res;
  }

  public void removeApp(NettyApp app) {
    apps.remove(app);
    if (apps.isEmpty())
      stop();
  }

  @Override
  public void start() throws IOException {
    if (started)
      throw new IllegalStateException();
    started = true;

    ensureExecutor();

    // nothing to bind: the application's own server already listens, and calls
    // us through the handler it added to its pipeline
    if (attached)
      return;

    bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
     .childOption(ChannelOption.TCP_NODELAY, java.lang.Boolean.TRUE)
     .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
     .channel(NioServerSocketChannel.class)
     .childHandler(new ServerInitializer(this))
    ;

    try {
      channel = b.bind(address).sync().channel();
      Log.debug("Server started on " + channel.localAddress());
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void stop() {
    if (!started)
      return;
    started = false;

    ExecutorService executor = this.executor;
    this.executor = null;

    if (attached) {
      // the socket and the event loops belong to the application: only the
      // threads we created are ours to release
      if (executor != null) {
        executor.shutdownNow();
        try {
          executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return;
    }

    bossGroup.shutdownGracefully(0, 10, TimeUnit.MILLISECONDS);
    workerGroup.shutdownGracefully(0, 10, TimeUnit.MILLISECONDS);

    try {
      if (channel != null)
        channel.closeFuture().await(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      // do not return while event loops are still running: a caller that stops
      // a server expects it to have released its threads, and a caller that
      // starts another one right away should not see them pile up
      bossGroup.terminationFuture().await(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      workerGroup.terminationFuture().await(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      if (executor != null) {
        executor.shutdownNow();
        executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    bossGroup = workerGroup = null;
    channel = null;
  }

  @Override
  public int getPort() {
    return getLocalAddress().getPort();
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    if (channel != null)
      return (InetSocketAddress) channel.localAddress();
    return address;
  }

  @Override
  public String getProtocol() {
    if (attached)
      return secure ? "https" : "http";
    return sslContext == null ? "http" : "https";
  }

  @Override
  public String getHostName() {
    return hostName;
  }

  @Override
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public void setPort(int port) {
    this.address = new InetSocketAddress(address.getAddress(), port);
  }

  public void setAddress(InetSocketAddress address) {
    this.address = address;
  }

  @Override
  public void setExecutor(ExecutorService executor) {
    if (started)
      throw new IllegalStateException("Server already started");
    this.executor = executor;
  }

  /**
   * Route handlers are free to block, so they never run on an event loop.
   * <p>
   * Created on demand: when attached, the application may well have plugged
   * our handler into a pipeline that is already serving before any app was
   * started.
   */
  public synchronized ExecutorService getExecutor() {
    return ensureExecutor();
  }

  private synchronized ExecutorService ensureExecutor() {
    if (executor == null)
      executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "flak-netty-handler");
        t.setDaemon(true);
        return t;
      });
    return executor;
  }

  public boolean isStarted() {
    return started;
  }
}
