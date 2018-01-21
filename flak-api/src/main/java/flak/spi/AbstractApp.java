package flak.spi;

import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import flak.App;
import flak.ContentTypeProvider;
import flak.ErrorHandler;
import flak.InputParser;
import flak.OutputFormatter;
import flak.Request;
import flak.RequestHandler;
import flak.Response;
import flak.SessionManager;
import flak.SuccessHandler;
import flak.UnknownPageHandler;
import flak.WebServer;
import flak.annotations.Route;
import flak.util.Log;

public abstract class AbstractApp implements App {

  /**
   * Optional URL where the app is plugged.
   */
  protected final String rootUrl;

  protected String sessionTokenCookie = "sessionToken";

  protected final Map<String, RequestHandler> handlers = new Hashtable<>();

  protected ContentTypeProvider mime = new DefaultContentTypeProvider();

  private final Map<String, OutputFormatter<?>> outputFormatterMap =
    new Hashtable<>();

  private final Map<String, InputParser> inputParserMap = new Hashtable<>();

  protected String loginPage;

  protected boolean requireLoggedInByDefault;

  protected SessionManager sessionManager = new DefaultSessionManager();

  protected Vector<ErrorHandler> errorHandlers = new Vector<>();

  protected Vector<SuccessHandler> successHandlers = new Vector<>();

  protected UnknownPageHandler unknownPageHandler;

  public AbstractApp(String rootUrl) {
    this.rootUrl = rootUrl;
  }

  public String getRootUrl() {
    String path = rootUrl == null ? "" : rootUrl;
    WebServer srv = getServer();
    return srv.getProtocol() + "://" + srv.getHostName() + ":" + srv.getPort() + path;
  }

  /**
   * Changes the name of the cookie in which the session token is stored. This
   * allows to have several web apps sharing a same host address (eg. using a
   * different port).
   */
  public void setSessionTokenCookie(String cookie) {
    this.sessionTokenCookie = cookie;
  }

  /**
   * Scans specified object for route handlers, i.e. public methods with @Route
   * annotation.
   *
   * @see Route
   */
  public App scan(Object obj) {
    for (Method method : obj.getClass().getMethods()) {
      Route route = method.getAnnotation(Route.class);
      if (route != null) {
        addHandler(route, method, obj);
      }
    }
    return this;
  }

  protected abstract void addHandler(Route route, Method method, Object obj);

  public App addOutputFormatter(String name, OutputFormatter<?> conv) {
    outputFormatterMap.put(name, conv);
    reconfigureHandlers();
    return this;
  }

  public OutputFormatter<?> getOutputFormatter(String name) {
    return outputFormatterMap.get(name);
  }

  @Override
  public App addInputParser(String name, InputParser inputParser) {
    inputParserMap.put(name, inputParser);
    reconfigureHandlers();
    return this;
  }

  public InputParser getInputParser(String name) {
    return inputParserMap.get(name);
  }

  protected String makeAbsoluteUrl(String uri) {
    if (rootUrl != null) {
      if (uri.startsWith("/"))
        uri = rootUrl + uri;
      else
        uri = rootUrl + "/" + uri;
    }
    return uri;
  }

  public void setContentTypeProvider(ContentTypeProvider mime) {
    this.mime = mime;
  }

  /**
   * Returns true if in DEBUG mode. When in debug mode, server stack traces are
   * sent to clients as body of the 500 response.
   */
  public boolean isDebugEnabled() {
    return Log.DEBUG;
  }

  /**
   * Marks current session as logged in (by setting a cookie).
   */
  public void loginUser(String login) {
    loginUser(login, false, makeRandomToken(login));
  }

  /**
   * Marks current session as logged in (by setting a cookie).
   */
  public void loginUser(String login, boolean rememberMe, String token) {
    sessionManager.createToken(token, login, rememberMe);
    getResponse().addHeader("Set-Cookie",
                            sessionTokenCookie + "=" + token + "; path=/;");
  }

  public void setSessionManager(SessionManager mgr) {
    this.sessionManager = mgr;
  }

  public String makeRandomToken(String login) {
    return (new Random().nextLong() ^ login.hashCode()) + "";
  }

  public Response redirectToLogin() {
    return redirect(loginPage);
  }

  /**
   * Sets the path of the login page, to which redirect all URLs that require a
   * logged in user. This method can be called directly, or otherwise one of
   * the URL handler methods can be annotated with @LoginPage.
   *
   * @param path the path of the login page
   */
  public void setLoginPage(String path) {
    this.loginPage = makeAbsoluteUrl(path);
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
    reconfigureHandlers();
  }

  protected abstract void reconfigureHandlers();

  /**
   * Returns the default policy for checking whether user must be logged in to
   * access all URLs by default.
   */
  public boolean getRequireLoggedInByDefault() {
    return requireLoggedInByDefault;
  }

  /**
   * Adds a handler that will be notified whenever a request is rejected
   */
  public void addErrorHandler(ErrorHandler hook) {
    errorHandlers.add(hook);
  }

  /**
   * Adds a handler that will be notified whenever a request is successful
   */
  public void addSuccessHandler(SuccessHandler hook) {
    successHandlers.add(hook);
  }

  public void fireError(int status, Request req, Throwable t) {
    for (ErrorHandler errorHandler : errorHandlers) {
      try {
        errorHandler.onError(status, req, t);
      }
      catch (Exception e) {
        Log.error(e, e);
      }
    }
  }

  public void fireSuccess(Method method, Object[] args, Object res) {
    Request r = getRequest();
    for (SuccessHandler successHandler : successHandlers) {
      successHandler.onSuccess(r, method, args, res);
    }
  }

  /**
   * Experimental. Allows to handle a request for an URL with no handler.
   * Requires
   * a root handler to be set somewhere (i.e. Route("/").
   */
  public void setUnknownPageHandler(UnknownPageHandler unknownPageHandler) {
    this.unknownPageHandler = unknownPageHandler;
  }
}
