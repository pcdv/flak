package flak.login;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashSet;

import flak.HttpException;
import flak.spi.AbstractMethodHandler;
import flak.spi.BeforeHook;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class CheckPermission implements BeforeHook {

  private final SessionManager manager;

  private final HashSet<String> permissions = new HashSet<>();

  public CheckPermission(AbstractMethodHandler handler,
                         SessionManager manager) {
    this.manager = manager;

    WithPermission perm =
      handler.getJavaMethod().getAnnotation(WithPermission.class);

    if (perm != null)
      permissions.add(perm.value());

    WithAnyPermission perms =
      handler.getJavaMethod().getAnnotation(WithAnyPermission.class);

    if (perms != null)
      Collections.addAll(permissions, perms.value());
  }

  @Override
  public void execute(SPRequest request) {
    if (!permissions.isEmpty()) {
      FlakSession session = manager.getCurrentSession(request);
      if (session == null || !hasPermission(session.getUser())) {
        throw new HttpException(HttpURLConnection.HTTP_UNAUTHORIZED, "No permission");
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
