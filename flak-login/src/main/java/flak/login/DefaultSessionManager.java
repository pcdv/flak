package flak.login;

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.LongSupplier;

import flak.App;
import flak.Request;
import flak.Response;
import flak.spi.util.Log;

/**
 * Example implementation of a session manager. It can be used as a basis for a new
 * implementation (you don't have to implement the SessionManager interface, the
 * SessionManager0 should suffice), or be extended.
 * <p>
 * Note that the implementation is non-persistent,
 * you need to override methods {@link #addSession(FlakSession)} and
 * {@link #closeSession(FlakSession)}, as well as populating the sessions during
 * initialization.
 */
public class DefaultSessionManager implements SessionManager {

  protected final Map<String, FlakSession> sessions = new Hashtable<>();

  protected final Map<String, FlakUser> users = new Hashtable<>();

  protected String sessionCookieName = "sessionToken";

  protected String loginPage;

  protected boolean requireLoggedInByDefault;

  private LongSupplier timeProvider = System::currentTimeMillis;

  /**
   * Changes the name of the cookie in which the session token is stored. This
   * allows to have several web apps sharing a same host address (e.g. using a
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
  public FlakSession getSessionForToken(String token) {
    return sessions.get(token);
  }

  @Override
  @Deprecated
  public FlakSession openSession(App app, FlakUser user, Response r) {
    String token = generateSessionToken();
    // must still call this deprecated method to avoid breaking compatibility
    FlakSession session = addSession(user, token);
    setCookie(app, session, r);
    return session;
  }

  /**
   * Receives a newly created session object, persists it and sends back a token
   * via a Set-Cookie header.
   *
   * @param app      useful to obtain the app's path
   * @param session  the new session
   * @param response the response into which a cookie must be created
   * @return the session received as parameter
   */
  public FlakSession openSession(App app, FlakSession session, Response response) {
    setCookie(app, session, response);
    return addSession(session);
  }

  /**
   * Fills the Set-Cookie header in specified response.
   */
  public void setCookie(App app, FlakSession session, Response response) {
    String path = app.getPath();
    if (path == null || path.isEmpty())
      path = "/";
    response.addHeader("Set-Cookie", generateSetCookieHeader(path, session));
  }

  /**
   * Builds the value of the Set-Cookie header according to RFC 6265.
   */
  protected String generateSetCookieHeader(String path, FlakSession session) {
    StringBuilder s = new StringBuilder(128);
    s.append(sessionCookieName)
     .append('=').append(session.getAuthToken())
     .append("; path=").append(path);

    if (session.isHttpOnly())
      s.append("; HttpOnly");

    FlakSession.SameSite sameSite = session.getSameSite();
    if (sameSite != null) {
      s.append("; SameSite=").append(sameSite);
    }

    if (session.getExpiry() > 0) {
      SimpleDateFormat format
        = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
      format.setTimeZone(TimeZone.getTimeZone("GMT"));
      Date expiresDate = new Date(session.getExpiry());
      s.append("; Expires=").append(format.format(expiresDate)).append(';');
    }

    return s.toString();
  }

  /**
   * Kept to maintain compile compatibility with classes that override the method. This
   * method is not called anymore and will be deleted soon.
   */
  @Deprecated
  protected String generateCookie(String token, String path) {
    return null;
  }

  @Deprecated
  public FlakSession addSession(FlakUser user, String token) {
    return addSession(new DefaultFlakSession(user, token));
  }

  public FlakSession addSession(FlakSession session) {
    sessions.put(session.getAuthToken(), session);
    return session;
  }

  /**
   * Generates a session token. Naive implementation that uses UUID.
   */
  public String generateSessionToken() {
    return UUID.randomUUID().toString();
  }

  /**
   * Removes a session. Should typically be overridden in order to remove the session
   * from persistence (don't forget to call super).
   */
  @Override
  public void closeSession(FlakSession session) {
    sessions.remove(session.getAuthToken());
  }

  /**
   * Shorthand for closing the session associated with a request, if any.
   */
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
    FlakSession session = sessions.get(token);
    if (session != null) {
      if (!isSessionValid(session)) {
        this.closeSession(session);
        session = null;
      }
    }
    return session != null;
  }

  protected boolean isSessionValid(FlakSession session) {
    return session.getExpiry() == 0 || timeProvider.getAsLong() < session.getExpiry();
  }

  public void redirectToLogin(Response resp) {
    resp.redirect(loginPage);
  }

  /**
   * Sets the path of the login page, to which redirect all URLs that require a
   * logged-in user. This method can be called directly, or otherwise one of
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
   *             except when annotated with @LoginNotRequired. If false, only handlers
   *             annotated with @LoginRequired will be protected
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
   * to openSession().
   * <p/>
   * If the user is logged in or if the URL being accessed is the login page,
   * the method simply returns true. Otherwise, if the path of the login page
   * has been set using @LoginPage or setLoginPage(), the user is redirected to
   * it. Otherwise, a 403 error is returned.
   */
  public boolean checkLoggedIn(Request r) {
    if (isLoggedIn(r)) {
      return true;
    }
    else {
      if (loginPage != null) {
        if (r.getPath().equals(loginPage))
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

  public String getAuthTokenCookieName() {
    return sessionCookieName;
  }

  public int getSessionCount() {
    return sessions.size();
  }

  /**
   * Mainly useful for testing with a controlled clock (which is used for validating
   * session expiration).
   */
  public void setTimeProvider(LongSupplier timeProvider) {
    this.timeProvider = timeProvider;
  }
}
