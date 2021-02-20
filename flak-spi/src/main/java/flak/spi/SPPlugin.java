package flak.spi;

import flak.FlakPlugin;

/**
 * @author pcdv
 */
public interface SPPlugin extends FlakPlugin {

  /**
   * Called before method handler is initialized. Allows to inspect the annotations
   * of the method, inject hooks, customize input parser or output formatter.
   */
  void preInit(AbstractMethodHandler handler);
}
