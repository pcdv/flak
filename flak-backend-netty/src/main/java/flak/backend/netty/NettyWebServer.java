package flak.backend.netty;

import flak.WebServer;
import flak.spi.util.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class NettyWebServer implements WebServer {

  private final Vector<NettyApp> apps = new Vector<>();
  private String hostName = "localhost";
  private int port;
  private Channel channel;
  private NioEventLoopGroup bossGroup;
  private NioEventLoopGroup workerGroup;
  private boolean started;

  public NettyWebServer() {
  }

  @Override
  public void setSSLContext(SSLContext context) {
    throw new RuntimeException("TODO");
  }

  public void addApp(NettyApp app) {
    apps.add(app);
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

    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
     .childOption(ChannelOption.TCP_NODELAY, java.lang.Boolean.TRUE)
     .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
     .channel(NioServerSocketChannel.class)
     .childHandler(new ServerInitializer(apps.get(0))) // FIXME
    ;

    try {
      channel = b.bind(port).sync().channel();
      System.out.println("Server started: http://127.0.0.1:" + port + '/');
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void stop() {
    bossGroup.shutdownGracefully(0, 10, TimeUnit.MILLISECONDS);
    workerGroup.shutdownGracefully(0, 10, TimeUnit.MILLISECONDS);
    try {
      channel.closeFuture().sync();
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String getProtocol() {
    return "http"; // FIXME
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
    this.port = port;
  }

  @Override
  public void setExecutor(ExecutorService executor) {
    Log.warn("NettyWebServer.setExecutor not implemented");
  }

  public boolean isStarted() {
    return started;
  }
}
