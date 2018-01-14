package net.jflask;

import flak.App;
import flak.AppFactory;
import net.jflask.sun.JdkWebServer;

/**
 * @author pcdv
 */
public class JdkAppFactory implements AppFactory {

  private JdkWebServer server;

  public JdkAppFactory() {
  }

  @Override
  public JdkWebServer getServer() {
    if (server == null)
      server = new JdkWebServer();
    return server;
  }

  @Override
  public App createApp() {
    JdkApp app = new JdkApp(getServer());
    getServer().addApp(app);
    return app;
  }

  @Override
  public App createApp(String appRootPath) {
    JdkApp app = new JdkApp(appRootPath, getServer());
    getServer().addApp(app);
    return app;
  }

  @Override
  public void setHttpPort(int httpPort) {
    getServer().setPort(httpPort);
  }

  @Override
  public int getHttpPort() {
    return getServer().getPort();
  }
}
