package flak.backend.jdk;

import flak.App;
import flak.spi.AbstractAppFactory;
import flak.spi.PluginUtil;

/**
 * @author pcdv
 */
public class JdkAppFactory extends AbstractAppFactory {

  private final JdkWebServer server;

  public JdkAppFactory() {
    server = new JdkWebServer();
  }

  @Override
  public JdkWebServer getServer() {
    return server;
  }

  @Override
  public App createApp() {
    JdkApp app = new JdkApp(getServer());
    PluginUtil.loadPlugins(app, pluginValidator);
    return app;
  }

  @Override
  public App createApp(String appRootPath) {
    JdkApp app = new JdkApp(appRootPath, getServer());
    PluginUtil.loadPlugins(app, pluginValidator);
    return app;
  }

  @Override
  public void setPort(int port) {
    getServer().setPort(port);
  }

  @Override
  public int getPort() {
    return getServer().getPort();
  }

}
