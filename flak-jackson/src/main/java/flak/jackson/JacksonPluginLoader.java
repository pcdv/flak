package flak.jackson;

import flak.App;
import flak.spi.FlakPluginLoader;

/**
 * @author pcdv
 */
public class JacksonPluginLoader implements FlakPluginLoader {
  @Override
  public void installPlugin(App app) {
    new JacksonPlugin(app).init();
  }
}
