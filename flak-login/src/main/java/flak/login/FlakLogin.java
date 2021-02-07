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

  private DefaultSessionManager sessionManager;

  private final AbstractApp app;

  public FlakLogin(App app) {
    this.app = (AbstractApp) app;
    this.sessionManager = new DefaultSessionManager();
  }

  public FlakLogin setSessionManager(DefaultSessionManager sessionManager) {
    this.sessionManager = sessionManager;
    return this;
  }

  public FlakLogin install() {

    app.addPlugin(this);

    app.addCustomExtractor(SessionManager.class, new ArgExtractor<SessionManager>(-1) {
      @Override
      public SessionManager extract(SPRequest request) {
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
    // TODO do not systematically add hooks (if access is not restricted)
    handler.addHook(new CheckLoggedIn(handler, sessionManager));
    handler.addHook(new CheckPermission(handler, sessionManager));
  }
}
