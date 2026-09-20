package flak;

import java.net.InetSocketAddress;
import java.util.function.Predicate;

/**
 * This is Flak's entry point for creating a web application. The
 * implementation depends on the backend being used.
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
   * Specifies exactly which plugins must be installed in the apps created by
   * this factory, e.g.
   * <pre>
   * factory.setPlugins(JacksonPlugin.class, FlakLogin.class);
   * </pre>
   * Automatic discovery of the plugins present in the classpath is disabled:
   * only the specified ones are installed, in the specified order. Calling
   * this method with no argument installs no plugin at all.
   * <p>
   * Unlike {@link #setPluginValidator(Predicate)}, which can only filter out
   * what happens to be in the classpath, this fails fast if a plugin is
   * missing. If both are set, the validator is ignored.
   * <p>
   * The arguments are the plugin classes themselves (e.g.
   * <code>FlakLogin.class</code>), not their loaders. A class that is not a
   * plugin, or whose module is absent from the classpath, is rejected with an
   * explicit error.
   */
  void setPlugins(Class<?>... plugins);

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
