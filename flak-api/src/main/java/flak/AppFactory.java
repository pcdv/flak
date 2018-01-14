package flak;

/**
 * @author pcdv
 */
public interface AppFactory {
  WebServer getServer();

  App createApp();

  App createApp(String appRootPath);

  void setHttpPort(int httpPort);

  int getHttpPort();
}
