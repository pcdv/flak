package flak.login;

import java.lang.reflect.AnnotatedElement;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashSet;

import flak.BeforeHook;
import flak.HttpException;
import flak.Request;
import flak.spi.AbstractMethodHandler;

/**
 * Rejects the requests of users lacking the permissions required by
 * {@link WithPermission} or {@link WithAnyPermission}, found on the handler
 * method or else on its class.
 *
 * @author pcdv
 */
public class CheckPermission implements BeforeHook {

  private final SessionManager0 manager;

  private final HashSet<String> permissions = new HashSet<>();

  public CheckPermission(AbstractMethodHandler handler,
                         SessionManager0 manager) {
    this.manager = manager;
    // the annotations of the method replace those of the class, so that a
    // class can restrict all its routes but a few. The class is that of the
    // target rather than the one declaring the method, so that a subclass
    // can restrict the routes it inherits
    if (!addPermissions(handler.getJavaMethod()))
      addPermissions(handler.getTarget().getClass());
  }

  private boolean addPermissions(AnnotatedElement e) {
    WithPermission perm = e.getAnnotation(WithPermission.class);
    if (perm != null)
      permissions.add(perm.value());
    WithAnyPermission perms = e.getAnnotation(WithAnyPermission.class);
    if (perms != null)
      Collections.addAll(permissions, perms.value());
    return perm != null || perms != null;
  }

  @Override
  public void execute(Request request) throws StopProcessingException {
    if (!permissions.isEmpty()) {
      // a permission implies a logged-in user: one who is not is treated as
      // with @LoginRequired, i.e. redirected to the login page or given 401.
      // NB: this is also what discards an expired session, which
      // getCurrentSession() would still return
      if (!manager.checkLoggedIn(request))
        throw STOP;
      FlakSession session = manager.getCurrentSession(request);
      if (session == null || !hasPermission(session.getUser())) {
        throw new HttpException(HttpURLConnection.HTTP_FORBIDDEN, "No permission");
      }
    }
  }

  private boolean hasPermission(FlakUser user) {
    for (String p : permissions) {
      if (user.hasPermission(p))
        return true;
    }
    return false;
  }
}
