package flak.backend.netty;

import flak.App;
import flak.WebServer;
import flak.spi.AbstractAppFactory;
import flak.spi.PluginUtil;

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
    PluginUtil.loadPlugins(app, pluginValidator);
    return app;
  }

  @Override
  public App createApp(String appRootPath) {
    throw new RuntimeException("TODO");
  }

  @Override
  public void setPort(int port) {
    server.setPort(port);
  }

  @Override
  public int getPort() {
    throw new RuntimeException("TODO");
  }
}
