package flak.spi;

import flak.App;

/**
 * Installs a plugin into a Flak app. Discovered using Java ServiceLoader.
 *
 * @author pcdv
 */
public interface FlakPluginLoader {
  void installPlugin(App app);
}
