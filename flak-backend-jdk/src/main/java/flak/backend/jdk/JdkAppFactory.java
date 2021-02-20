package flak.backend.jdk;

import flak.App;
import flak.AppFactory;
import flak.FlakPlugin;
import flak.spi.PluginUtil;

import java.util.function.Predicate;

/**
 * @author pcdv
 */
public class JdkAppFactory implements AppFactory {

  private final JdkWebServer server;
  private Predicate<Class<? extends FlakPlugin>> pluginValidator;

  public JdkAppFactory() {
    server = new JdkWebServer();
  }

  @Override
  public JdkWebServer getServer() {
    return server;
  }

  /**
   * Allows to prevent the systematic installation of plugins present in the
   * classpath. The predicate will be evaluated ant the plugin will be installed
   * only if it returns true.
   */
  @Override
  public void setPluginValidator(Predicate<Class<? extends FlakPlugin>> pluginValidator) {
    this.pluginValidator = pluginValidator;
  }

  @Override
  public App createApp() {
    JdkApp app = new JdkApp(getServer());
    PluginUtil.loadPlugins(app, pluginValidator);
    getServer().addApp(app);
    return app;
  }

  @Override
  public App createApp(String appRootPath) {
    JdkApp app = new JdkApp(appRootPath, getServer());
    PluginUtil.loadPlugins(app, pluginValidator);
    getServer().addApp(app);
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
