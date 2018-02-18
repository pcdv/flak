package flak.login;

import flak.App;
import flak.Request;
import flak.Response;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public interface SessionManager {

  void setRequireLoggedInByDefault(boolean b);

  boolean getRequireLoggedInByDefault();

  void redirectToLogin(Response response);

  /**
   * Checks whether request is associated to an active session. If not,
   * either reject the request or redirect it to login page.
   */
  boolean checkLoggedIn(SPRequest r);

  /**
   * Sets the login page to redirect to.
   */
  void setLoginPage(String path);

  void setAuthTokenCookieName(String name);

  FlakSession getCurrentSession(Request r);

  FlakSession openSession(App app, FlakUser user, Response r);

  void closeSession(FlakSession session);

  void closeCurrentSession(Request request);

  FlakUser getUser(String id);
}
