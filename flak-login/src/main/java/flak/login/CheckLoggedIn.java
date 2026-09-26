package flak.login;

import java.lang.reflect.Method;

import flak.BeforeHook;
import flak.Request;
import flak.spi.AbstractMethodHandler;
import flak.spi.RestrictedTarget;

/**
 * @author pcdv
 */
public class CheckLoggedIn implements BeforeHook {

  private final Boolean loginRequired;

  private final AbstractMethodHandler handler;

  private final SessionManager0 manager;

  private final Method method;

  CheckLoggedIn(AbstractMethodHandler handler, SessionManager0 manager) {
    this.handler = handler;
    this.manager = manager;
    this.method = handler.getJavaMethod();
    this.loginRequired = initLoginRequired();
    if (method.getAnnotation(LoginPage.class) != null)
      manager.setLoginPage(handler.getRoute());
  }

  private Boolean initLoginRequired() {
    if (method.getAnnotation(LoginRequired.class) != null)
      return true;

    if (method.getAnnotation(LoginPage.class) != null || method.getAnnotation(
      LoginNotRequired.class) != null)
      return false;

    if (handler.getTarget() instanceof RestrictedTarget)
      return ((RestrictedTarget) handler.getTarget()).isRestricted();

    return null;
  }

  private boolean isLoginRequired() {
    return loginRequired != null ? loginRequired
                                 : manager.getRequireLoggedInByDefault();
  }

  @Override
  public void execute(Request req) throws StopProcessingException {
    if (isLoginRequired() && !manager.checkLoggedIn(req)) {
      throw STOP;
    }
  }
}
