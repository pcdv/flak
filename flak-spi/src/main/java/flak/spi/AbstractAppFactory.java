package flak.spi;

import flak.App;
import flak.AppFactory;
import flak.FlakPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public abstract class AbstractAppFactory implements AppFactory {
  protected Predicate<Class<? extends FlakPlugin>> pluginValidator;

  /**
   * The plugins explicitly requested with setPlugins(), or null when they must
   * be discovered in the classpath (an empty list means "no plugin at all").
   */
  protected List<Class<?>> plugins;

  /**
   * Allows to prevent the systematic installation of plugins present in the
   * classpath. The predicate will be evaluated ant the plugin will be installed
   * only if it returns true.
   */
  @Override
  public void setPluginValidator(Predicate<Class<? extends FlakPlugin>> pluginValidator) {
    this.pluginValidator = pluginValidator;
  }

  @Override
  public void setPlugins(Class<?>... plugins) {
    this.plugins = Arrays.asList(plugins);
  }

  /**
   * Installs the plugins in a freshly created app. To be called by every
   * backend at the end of createApp().
   */
  protected void installPlugins(App app) {
    if (plugins == null)
      PluginUtil.loadPlugins(app, pluginValidator);
    else
      PluginUtil.loadPlugins(app, plugins);
  }
}
