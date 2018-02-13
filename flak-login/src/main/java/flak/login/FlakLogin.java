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

  private App app;

  public FlakLogin(App app) {
    this.app = app;
    this.sessionManager = new DefaultSessionManager(app);

    ((AbstractApp) app).addPlugin(this);

    ((AbstractApp) app)

      .addCustomExtractor(SessionManager.class, new ArgExtractor(-1) {
        @Override
        public Object extract(SPRequest request) throws Exception {
          return sessionManager;
        }
      });
  }

  public DefaultSessionManager getSessionManager() {
    return sessionManager;
  }

  @Override
  public void onNewHandler(AbstractMethodHandler handler) {
    handler.addHook(new CheckLoggedIn(handler, sessionManager));
  }
}
