package flak.login;

import java.net.HttpURLConnection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;

import flak.App;
import flak.Request;
import flak.Response;
import flak.spi.SPRequest;
import flak.spi.util.Log;

/**
 * Default, non-persistent, implementation of SessionManager.
 *
 * @author pcdv
 */
public class DefaultSessionManager implements SessionManager {

  private Map<String, String> sessions = new Hashtable<>();

  private String sessionCookieName = "sessionToken";

  private String loginPage;

  private boolean requireLoggedInByDefault;

  private App app;

  DefaultSessionManager(App app) {
    this.app = app;
  }

  /**
   * Changes the name of the cookie in which the session token is stored. This
   * allows to have several web apps sharing a same host address (eg. using a
   * different port).
   */
  public void setSessionCookieName(String name) {
    this.sessionCookieName = name;
  }

  private void createToken(String token, String login, boolean rememberMe) {
    // TODO: store more useful info about the user
    sessions.put(token, login);
  }

  private boolean isTokenValid(String token) {
    return sessions.containsKey(token);
  }

  private void removeToken(String token) {
    sessions.remove(token);
  }

  /**
   * Marks current session as logged in (by setting a cookie).
   */
  public void loginUser(String login) {
    loginUser(login, false, makeRandomToken(login));
  }

  private String makeRandomToken(String login) {
    return (new Random().nextLong() ^ login.hashCode()) + "";
  }

  /**
   * Marks current session as logged in (by setting a cookie).
   */
  private void loginUser(String login, boolean rememberMe, String token) {
    createToken(token, login, rememberMe);
    app.getResponse()
       .addHeader("Set-Cookie", sessionCookieName + "=" + token + "; path=/;");
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
   * Returns the login bound with current request (i.e. the one that has been
   * associated with session using {@link #loginUser(String)}.
   *
   * @return current request login, null if none
   */
  public String getCurrentLogin() {
    String token = app.getRequest().getCookie(sessionCookieName);
    if (token == null)
      return null;

    // method below will fail if we have a token BUT user is unknown or logged out
    return sessions.get(token);
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
  public boolean isLoggedIn() {
    return isLoggedIn(app.getRequest());
  }

  private boolean isLoggedIn(Request r) {
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
  public boolean checkLoggedIn(SPRequest r) {
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

  /**
   * Call this method to destroy the current session, i.e. make the user
   * appearing as "not logged in".
   *
   * @see LoginRequired
   */
  public void logoutUser() {
    Request req = app.getRequest();
    String token = req.getCookie(sessionCookieName);
    if (token != null)
      removeToken(token);
  }
}
