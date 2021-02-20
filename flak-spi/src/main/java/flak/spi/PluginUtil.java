package flak.spi;

import java.util.ServiceLoader;
import java.util.function.Predicate;

import flak.App;
import flak.FlakPlugin;
import flak.spi.util.Log;

/**
 * @author pcdv
 */
public class PluginUtil {

  /**
   * Discovers available plugins and installs them in the Flak application.
   */
  public static void loadPlugins(App app, Predicate<Class<? extends FlakPlugin>> validator) {
    for (FlakPluginLoader loader : ServiceLoader.load(FlakPluginLoader.class)) {
      if (validator == null || validator.test(loader.getPluginClass())) {
        Log.debug("Installing plugin using " + loader);
        loader.installPlugin(app);
      }
      else {
        Log.debug("Plugin skipped by validator: "+loader.getPluginClass());
      }
    }
  }
}
