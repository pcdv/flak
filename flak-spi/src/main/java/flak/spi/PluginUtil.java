package flak.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

  /**
   * Installs exactly the specified plugins, in the specified order, without
   * discovering anything else.
   *
   * @param plugins the plugin classes, each of which must have a
   * FlakPluginLoader in the classpath
   * @throws IllegalArgumentException if a plugin cannot be found: a silent
   * absence would be much harder to diagnose than a missing dependency
   */
  public static void loadPlugins(App app, List<Class<?>> plugins) {
    if (plugins.isEmpty())
      return;

    Map<Class<?>, FlakPluginLoader> loaders = new LinkedHashMap<>();
    for (FlakPluginLoader loader : ServiceLoader.load(FlakPluginLoader.class)) {
      loaders.put(loader.getPluginClass(), loader);
    }

    // check everything before installing anything, so a typo cannot leave a
    // half configured app behind
    List<Class<?>> missing = plugins.stream()
                                    .filter(c -> !loaders.containsKey(c))
                                    .collect(Collectors.toList());
    if (!missing.isEmpty())
      throw new IllegalArgumentException(describeMissing(missing, loaders.keySet()));

    for (Class<?> plugin : plugins) {
      Log.debug("Installing plugin " + plugin.getName());
      loaders.get(plugin).installPlugin(app);
    }
  }

  private static String describeMissing(List<Class<?>> missing,
                                        Iterable<Class<?>> available) {
    List<String> names = new ArrayList<>();
    for (Class<?> c : available)
      names.add(c.getName());

    StringBuilder s = new StringBuilder("No plugin found for ");
    s.append(missing.stream().map(Class::getName).collect(Collectors.joining(", ")));
    s.append(". Is the module present in the classpath? Available plugins: ");
    s.append(names.isEmpty() ? "none" : String.join(", ", names));
    return s.toString();
  }
}
