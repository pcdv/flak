package flak;

import java.net.InetSocketAddress;
import java.util.function.Predicate;

/**
 * This is Flak's entry point for creating a web application. The
 * implementation depends on the backend being used (there is only one for now).
 * The factory is obtained with {@link Flak#getFactory()}.
 *
 * @author pcdv
 */
public interface AppFactory {

  /**
   * Returns the associated web server (there is one per factory).
   */
  WebServer getServer();

  void setPluginValidator(Predicate<Class<? extends FlakPlugin>> pluginValidator);

  /**
   * Creates a new web app, with an implicit root path.
   */
  App createApp();

  /**
   * Creates a new web app hosted at specified path. Several apps can be
   * hosted on the same web server provided they use separate paths.
   */
  App createApp(String appRootPath);

  /**
   * Sets the port of the web server. This method can be called only before
   * any App is started.
   */
  void setPort(int port);

  /**
   * Sets the socket address the web server will listen to.
   * This method can be called only before any App is started.
   */
  void setLocalAddress(InetSocketAddress address);

  /**
   * Returns the port of the web server. This is equivalent to calling
   * <code>getServer().getPort()</code>.
   */
  default int getPort() {
    return getServer().getPort();
  }
}
