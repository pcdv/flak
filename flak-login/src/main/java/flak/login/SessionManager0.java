package flak.login;

import flak.Request;
import flak.Response;

/**
 * Minimal requirements for a session manager, i.e. methods that are called by
 * the internal API.
 * <p>
 * Since 2.4, we want to minimize the surface of the session
 * manager API to give more flexibility to implementations. Custom session manager
 * can define their own API which can be used from the HTTP handlers of the application.
 */
public interface SessionManager0 {
  void setRequireLoggedInByDefault(boolean b);

  boolean getRequireLoggedInByDefault();

  void redirectToLogin(Response response);

  /**
   * Checks whether request is associated to an active session. If not,
   * either reject the request or redirect it to login page.
   */
  boolean checkLoggedIn(Request r);

  /**
   * Sets the login page to redirect to. Called from annotation CheckLoggedIn.
   */
  void setLoginPage(String path);

  FlakSession getCurrentSession(Request r);
}
