package flak.login;

import flak.App;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.ArgExtractor;
import flak.spi.FlakPlugin;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class FlakLogin implements FlakPlugin {

  private final DefaultSessionManager sessionManager;

  private final AbstractApp app;

  public FlakLogin(App app) {
    this.app = (AbstractApp) app;
    this.sessionManager = new DefaultSessionManager(app);
  }

  public FlakLogin install() {

    app.addPlugin(this);

    app.addCustomExtractor(SessionManager.class, new ArgExtractor(-1) {
      @Override
      public Object extract(SPRequest request) {
        return sessionManager;
      }
    });

    return this;
  }

  public DefaultSessionManager getSessionManager() {
    return sessionManager;
  }

  @Override
  public void preInit(AbstractMethodHandler handler) {
    handler.addHook(new CheckLoggedIn(handler, sessionManager));
  }
}
