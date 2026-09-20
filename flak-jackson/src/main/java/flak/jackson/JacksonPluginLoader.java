package flak.jackson;

import flak.App;
import flak.FlakPlugin;
import flak.spi.FlakPluginLoader;

/**
 * @author pcdv
 */
public class JacksonPluginLoader implements FlakPluginLoader {
  @Override
  public void installPlugin(App app) {
    app.addPlugin(new JacksonPlugin());
  }

  @Override
  public Class<? extends FlakPlugin> getPluginClass() {
    return JacksonPlugin.class;
  }
}
