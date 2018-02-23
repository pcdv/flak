package flak.login;

import java.net.HttpURLConnection;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import flak.App;
import flak.Request;
import flak.Response;
import flak.spi.util.Log;

/**
 * Default, non-persistent, implementation of SessionManager.
 *
 * @author pcdv
 */
public class DefaultSessionManager implements SessionManager {

  private Map<String, FlakSession> sessions = new Hashtable<>();

  private String sessionCookieName = "sessionToken";

  private String loginPage;

  private boolean requireLoggedInByDefault;

  private Map<String, FlakUser> users = new Hashtable<>();

  /**
   * Changes the name of the cookie in which the session token is stored. This
   * allows to have several web apps sharing a same host address (eg. using a
   * different port).
   */
  public void setAuthTokenCookieName(String name) {
    this.sessionCookieName = name;
  }

  @Override
  public FlakSession getCurrentSession(Request r) {
    String token = r.getCookie(sessionCookieName);
    if (token == null)
      return null;

    return sessions.get(token);
  }

  @Override
  public FlakSession openSession(App app, FlakUser user, Response r) {
    String token = generateToken();
    FlakSession session = addSession(user, token);
    r.addHeader("Set-Cookie",
                sessionCookieName + "=" + token + "; path=" + app.getPath() + ";");
    return session;
  }

  public FlakSession addSession(FlakUser user, String token) {
    DefaultFlakSession session = new DefaultFlakSession(user, token);
    sessions.put(token, session);
    return session;
  }

  private String generateToken() {
    return UUID.randomUUID().toString();
  }

  @Override
  public void closeSession(FlakSession session) {
    sessions.remove(session.getAuthToken());
  }

  @Override
  public void closeCurrentSession(Request request) {
    FlakSession session = getCurrentSession(request);
    if (session != null)
      closeSession(session);
  }

  @Override
  public FlakUser getUser(String id) {
    return users.get(id);
  }

  private boolean isTokenValid(String token) {
    return sessions.containsKey(token);
  }

  public void redirectToLogin(Response resp) {
    resp.redirect(loginPage);
  }

  /**
   * Sets the path of the login page, to which redirect all URLs that require a
   * logged in user. This method can be called directly, or otherwise one of
   * the URL handler methods can be annotated with @LoginPage.
   *
   * @param path the path of the login page
   */
  public void setLoginPage(String path) {
    this.loginPage = path;
  }

  /**
   * Sets the default policy for checking whether user must be logged in to
   * access all URLs by default.
   *
   * @param flag if true, all URL handlers require the user to be logged in
   * except when annotated with @LoginNotRequired. If false, only handlers
   * annotated with @LoginRequired will be protected
   */
  public void setRequireLoggedInByDefault(boolean flag) {
    this.requireLoggedInByDefault = flag;
  }

  /**
   * Returns the default policy for checking whether user must be logged in to
   * access all URLs by default.
   */
  public boolean getRequireLoggedInByDefault() {
    return requireLoggedInByDefault;
  }

  /**
   * Can be called from a request handler to determine whether current
   * session is authenticated.
   */
  public boolean isLoggedIn(Request r) {
    String token = r.getCookie(sessionCookieName);
    return (token != null && isTokenValid(token));
  }

  /**
   * Checks that the user is currently logged in. This is performed by looking
   * at the "sessionToken" cookie that has been set in session during last call
   * to createSession().
   * <p/>
   * If the user is logged in or if the URL being accessed is the login page,
   * the method simply returns true. Otherwise, if the path of the login page
   * has been set using @LoginPage or setLoginPage(), the user is redirected to
   * it. Otherwise a 403 error is returned.
   */
  public boolean checkLoggedIn(Request r) {
    if (isLoggedIn(r)) {
      return true;
    }
    else {
      if (loginPage != null) {
        if (r.getPath().startsWith(loginPage))
          return true;
        r.getResponse().redirect(loginPage + "?url=" + r.getPath());
      }
      else {
        Log.debug("Forbidden (not logged in): " + r.getPath());
        r.getResponse().setStatus(HttpURLConnection.HTTP_FORBIDDEN);
      }
      return false;
    }
  }

  public FlakUser createUser(String login) {
    return new DefaultUser(login);
  }

  public void addUser(FlakUser user) {
    users.put(user.getId(), user);
  }
}
