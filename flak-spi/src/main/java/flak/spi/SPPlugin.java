package flak.spi;

import flak.FlakPlugin;

/**
 * @author pcdv
 */
public interface SPPlugin extends FlakPlugin {

  /**
   * Called when the plugin is installed in an app, before any handler is
   * passed to preInit(). This is where a plugin registers its argument
   * extractors, formatters or parsers.
   */
  default void install() {
  }

  /**
   * Called before method handler is initialized. Allows to inspect the annotations
   * of the method, inject hooks, customize input parser or output formatter.
   */
  void preInit(AbstractMethodHandler handler);

  /**
   * Tells whether this plugin keeps the handlers of a {@link RestrictedTarget}
   * away from users who are not logged in. Without such a plugin, serving
   * restricted resources fails, since nothing would protect them.
   */
  default boolean enforcesRestrictions() {
    return false;
  }
}
