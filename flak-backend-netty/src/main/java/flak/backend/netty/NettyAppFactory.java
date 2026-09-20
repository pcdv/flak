package flak.backend.netty;

import flak.App;
import flak.spi.AbstractAppFactory;

import java.net.InetSocketAddress;

public class NettyAppFactory extends AbstractAppFactory {
  private final NettyWebServer server;

  public NettyAppFactory() {
    server = new NettyWebServer();
  }

  private NettyAppFactory(InetSocketAddress address, boolean secure) {
    server = new NettyWebServer(true, address, secure);
  }

  /**
   * Creates a factory for an application that runs its own netty server: flak
   * binds nothing, and serves the requests handed to it by
   * <code>getServer().getHttpHandler()</code>, which the application adds to
   * the pipeline it builds. This leaves the application free to serve other
   * protocols, e.g. websockets, on the same port.
   * <p>
   * The address is only what flak advertises in {@link App#getRootUrl()}. It
   * can be set again with {@link #setLocalAddress(InetSocketAddress)} once the
   * application has bound its server, which is the only way to know the port
   * when binding on 0.
   *
   * @param address the address the application's server listens to, or null
   * @param secure whether that server is behind TLS, so that the URLs built by
   * flak use https
   */
  public static NettyAppFactory attached(InetSocketAddress address, boolean secure) {
    return new NettyAppFactory(address, secure);
  }

  @Override
  public NettyWebServer getServer() {
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
