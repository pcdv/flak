package flak.login;

import java.lang.reflect.Method;

import flak.BeforeHook;
import flak.spi.AbstractMethodHandler;
import flak.spi.RestrictedTarget;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class CheckLoggedIn implements BeforeHook {

  private final boolean loginRequired;

  private final AbstractMethodHandler handler;

  private final SessionManager manager;

  private final Method method;

  public CheckLoggedIn(AbstractMethodHandler handler, SessionManager manager) {
    this.handler = handler;
    this.manager = manager;
    this.method = handler.getJavaMethod();
    this.loginRequired = isLoginRequired();
    if (method.getAnnotation(LoginPage.class) != null)
      manager.setLoginPage(handler.getRoute());
  }

  private boolean isLoginRequired() {
    if (method.getAnnotation(LoginRequired.class) != null)
      return true;

    if (method.getAnnotation(LoginPage.class) != null || method.getAnnotation(
      LoginNotRequired.class) != null)
      return false;

    if (handler.getTarget() instanceof RestrictedTarget)
      return ((RestrictedTarget) handler.getTarget()).isRestricted();

    return manager.getRequireLoggedInByDefault();
  }

  @Override
  public void execute(SPRequest req) throws StopProcessingException {
    if (isLoginRequired() && !manager.checkLoggedIn(req)) {
      throw STOP;
    }
  }
}
