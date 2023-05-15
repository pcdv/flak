package flak.login;

import flak.App;
import flak.Request;
import flak.Response;

/**
 * The minimal set of methods to implement is in interface SessionManager0. Here,
 * we also have methods that happen to be implemented in DefaultSessionManager, but
 * they don't really need to be specified in the API.
 *
 * @author pcdv
 */
public interface SessionManager extends SessionManager0 {

  /**
   * Checks that the request contains a cookie that points to a valid session.
   */
  boolean isLoggedIn(Request r);

  /**
   * Returns the name of the cookie that is set in the Set-Cookie sent back to clients
   * when opening a session.
   */
  String getAuthTokenCookieName();

  /**
   * Sets the name of the cookie that is set in the Set-Cookie sent back to clients
   * when opening a session.
   */
  void setAuthTokenCookieName(String name);

  /**
   * @deprecated For more flexibility, it is recommended to first create a session,
   * then call {@link DefaultSessionManager#setCookie(App, FlakSession, Response)}
   * and {@link DefaultSessionManager#addSession(FlakSession)}.
   */
  @Deprecated
  FlakSession openSession(App app, FlakUser user, Response r);

  /**
   * Invalidates a session. A good use-case is a from /logout endpoint?
   */
  void closeSession(FlakSession session);

  /**
   * Shorthand for closing the session associated with a request.
   */
  void closeCurrentSession(Request request);

  /**
   * Optional method to retrieve a user from its id/login.
   * <p>
   * Should not have been part of the specification and be left to the user. Kept for
   * compatibility purposes.
   */
  FlakUser getUser(String id);

  /**
   * Optional method to create a user.
   * <p>
   * Should not be part of this interface and be left to the user. Kept for
   * compatibility purposes.
   */
  FlakUser createUser(String id);

  /**
   * Optional method to add a user to the known list of users.
   * <p>
   * Should not be part of this interface and be left to the user. Kept for
   * compatibility purposes.
   */
  void addUser(FlakUser user);
}
