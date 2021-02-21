package flak.spi;

import flak.AppFactory;
import flak.FlakPlugin;

import java.util.function.Predicate;

public abstract class AbstractAppFactory implements AppFactory {
  protected Predicate<Class<? extends FlakPlugin>> pluginValidator;

  /**
   * Allows to prevent the systematic installation of plugins present in the
   * classpath. The predicate will be evaluated ant the plugin will be installed
   * only if it returns true.
   */
  @Override
  public void setPluginValidator(Predicate<Class<? extends FlakPlugin>> pluginValidator) {
    this.pluginValidator = pluginValidator;
  }
}
