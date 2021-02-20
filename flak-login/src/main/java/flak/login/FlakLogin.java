package flak.login;

import flak.App;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.ArgExtractor;
import flak.spi.SPPlugin;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class FlakLogin implements SPPlugin {

  /**
   * We use a delegate because if setSessionManager() is called, the
   * previous manager may have been leaked in some handlers.
   */
  private final SessionManagerDelegate sessionManager;

  private final AbstractApp app;

  public FlakLogin(App app) {
    this.app = (AbstractApp) app;
    this.sessionManager = new SessionManagerDelegate(new DefaultSessionManager());
  }

  public FlakLogin setSessionManager(SessionManager sessionManager) {
    this.sessionManager.setDelegate(sessionManager);
    return this;
  }

  void install() {

    app.addPlugin(this);

    app.addCustomExtractor(SessionManager.class, new ArgExtractor<SessionManager>(-1) {
      @Override
      public SessionManager extract(SPRequest request) {
        return sessionManager;
      }
    });

    app.addCustomExtractor(FlakUser.class, new ArgExtractor<FlakUser>(-1) {
      @Override
      public FlakUser extract(SPRequest request) {
        FlakSession session = sessionManager.getCurrentSession(request);
        return session == null ? null : session.getUser();
      }
    });

  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }

  @Override
  public void preInit(AbstractMethodHandler handler) {
    // TODO do not systematically add hooks (if access is not restricted)
    handler.addHook(new CheckLoggedIn(handler, sessionManager));
    handler.addHook(new CheckPermission(handler, sessionManager));
  }
}
