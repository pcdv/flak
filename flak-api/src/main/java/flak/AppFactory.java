package flak;

/**
 * @author pcdv
 */
public interface AppFactory {
  WebServer getServer();

  App createApp();

  App createApp(String appRootPath);

  void setPort(int port);

  int getPort();
}
