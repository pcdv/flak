package flak.spi;

import flak.App;
import flak.FlakPlugin;

/**
 * Installs a plugin into a Flak app. Discovered using Java ServiceLoader.
 *
 * @author pcdv
 */
public interface FlakPluginLoader {
  void installPlugin(App app);

  Class<? extends FlakPlugin> getPluginClass();
}
