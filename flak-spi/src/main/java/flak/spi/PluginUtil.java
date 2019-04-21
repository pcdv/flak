package flak.spi;

import java.util.ServiceLoader;

import flak.App;
import flak.spi.util.Log;

/**
 * @author pcdv
 */
public class PluginUtil {

  /**
   * Discovers available plugins and installs them in the Flak application.
   */
  public static void loadPlugins(App app) {
    for (FlakPluginLoader loader : ServiceLoader.load(FlakPluginLoader.class)) {
      Log.debug("Installing plugin using " + loader);
      loader.installPlugin(app);
    }
  }
}
