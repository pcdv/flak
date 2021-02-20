package flak.login;

import java.net.HttpURLConnection;

import flak.HttpException;
import flak.spi.AbstractMethodHandler;
import flak.spi.BeforeHook;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class CheckPermission implements BeforeHook {

  private final SessionManager manager;

  private final String permission;

  public CheckPermission(AbstractMethodHandler handler,
                         SessionManager manager) {
    this.manager = manager;

    WithPermission perm =
      handler.getJavaMethod().getAnnotation(WithPermission.class);

    this.permission = perm == null ? null : perm.value();

  }

  @Override
  public void execute(SPRequest request) {
    if (permission != null) {
      FlakSession session = manager.getCurrentSession(request);
      if (session == null || !session.getUser().hasPermission(permission)) {
        throw new HttpException(HttpURLConnection.HTTP_UNAUTHORIZED,
                                "No permission");
      }
    }
  }
}
