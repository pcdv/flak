package flak.login;

import flak.App;
import flak.FlakPlugin;
import flak.spi.FlakPluginLoader;

/**
 * @author pcdv
 */
public class FlakLoginLoader implements FlakPluginLoader {
  @Override
  public void installPlugin(App app) {
    new FlakLogin(app).install();
  }

  @Override
  public Class<? extends FlakPlugin> getPluginClass() {
    return FlakLogin.class;
  }
}
