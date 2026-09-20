package flak.backend.netty;

import flak.App;
import flak.WebServer;
import flak.spi.AbstractAppFactory;

import java.net.InetSocketAddress;

public class NettyAppFactory extends AbstractAppFactory {
  private final NettyWebServer server;

  public NettyAppFactory() {
    server = new NettyWebServer();
  }

  @Override
  public WebServer getServer() {
    return server;
  }

  @Override
  public App createApp() {
    NettyApp app = new NettyApp(null, server);
    installPlugins(app);
    return app;
  }

  @Override
  public App createApp(String appRootPath) {
    NettyApp app = new NettyApp(appRootPath, server);
    installPlugins(app);
    return app;
  }

  @Override
  public void setPort(int port) {
    server.setPort(port);
  }

  @Override
  public void setLocalAddress(InetSocketAddress address) {
    server.setAddress(address);
  }

  @Override
  public int getPort() {
    return getServer().getPort();
  }
}
