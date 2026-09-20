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
    app.addPlugin(new FlakLogin(app));
  }

  @Override
  public Class<? extends FlakPlugin> getPluginClass() {
    return FlakLogin.class;
  }
}
