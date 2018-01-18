package net.jflask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import flak.App;
import flak.ContentTypeProvider;
import flak.ErrorHandler;
import flak.Request;
import flak.RequestHandler;
import flak.Response;
import flak.ResponseConverter;
import flak.SessionManager;
import flak.SuccessHandler;
import flak.UnknownPageHandler;
import flak.annotations.Route;
import flak.util.Log;
import net.jflask.sun.AbstractResourceHandler;
import net.jflask.sun.DefaultContentTypeProvider;
import net.jflask.sun.FileHandler;
import net.jflask.sun.JdkWebServer;
import net.jflask.sun.ResourceHandler;

/**
 * Encapsulates the server side of a web app: an HTTP server and some route
 * handlers. If some route handlers are defined in an external class (i.e. not
 * extending the main App), {@link #scan(Object)} must be called in order to
 * detect them in an instance of the class.
 * <p/>
 * The App can be extended with some handlers:
 * <p/>
 * <pre>
 * public class MyApp extends App {
 *   &#064;Route(value = &quot;/hello/:name&quot;)
 *   public String hello(String name) {
 *     return &quot;Hello &quot; + name;
 *   }
 * }
 * ...
 * new MyApp().start()
 * </pre>
 * <p/>
 * Or the App can be extended by calling scan():
 * <p/>
 * <pre>
 * public class MyApp {
 *   &#064;Route(value = &quot;/hello/:name&quot;)
 *   public String hello(String name) {
 *     return &quot;Hello &quot; + name;
 *   }
 * }
 * ...
 * App app = new App()
 * app.scan(new MyApp());
 * app.start();
 * </pre>
 *
 * @author pcdv
 */
public class JdkApp implements App {

  protected final JdkWebServer srv;

  /**
   * Optional URL where the app is plugged.
   */
  private final String rootUrl;

  private String sessionTokenCookie = "sessionToken";

  private final Map<String, RequestHandler> handlers = new Hashtable<>();

  private ContentTypeProvider mime = new DefaultContentTypeProvider();

  private final ThreadLocal<SunRequest> localRequest = new ThreadLocal<>();

  private final Map<String, ResponseConverter<?>> converters =
    new Hashtable<>();

  private String loginPage;

  private boolean requireLoggedInByDefault;

  private List<MethodHandler> allHandlers = new ArrayList<>(256);

  private SessionManager sessionManager = new DefaultSessionManager();

  private boolean started;

  private Vector<ErrorHandler> errorHandlers = new Vector<>();

  private Vector<SuccessHandler> successHandlers = new Vector<>();

  private UnknownPageHandler unknownPageHandler;

  public JdkApp(JdkWebServer server) {
    this(null, server);
  }

  public JdkApp(String rootUrl, JdkWebServer server) {
    this.srv = server;
    this.rootUrl = rootUrl;

    // in case we are extended by a subclass with annotations
    scan(this);
  }

  public String getRootUrl() {
    String path = rootUrl == null ? "" : rootUrl;
    // FIXME!
    return "http://localhost:" + getServer().getPort() + path;
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

  private void addHandler(Route route, Method m, Object obj) {
    String[] tok = route.value().split("/+");

    // split the static and dynamic part of the route (i.e. /app/hello/:name =>
    // "/app/hello" + "/:name"). The static part is used to get or create a
    // Context, the dynamic part is used to add a handler in the Context.
    StringBuilder root = new StringBuilder(80);
    StringBuilder rest = new StringBuilder(80);
    int i = 0;
    for (; i < tok.length; i++) {
      if (tok[i].isEmpty())
        continue;
      if (tok[i].startsWith(":") || tok[i].startsWith("*"))
        break;
      root.append('/').append(tok[i]);
    }

    for (; i < tok.length; i++) {
      rest.append('/').append(tok[i]);
    }

//    if (rest.length() == 0)
//      rest.append('/');

    MethodHandler handler =
      getContext(root.toString()).addHandler(rest.toString(), route, m, obj);

    allHandlers.add(handler);
  }

  /**
   * Gets or creates a Context for specified root URI.
   */
  private Context getContext(String rootURI) {
    if ("/".equals(rootURI))
      rootURI = "";

    RequestHandler c = handlers.get(rootURI);

    if (c == null) {
      Log.debug("Creating context for " + rootURI);
      handlers.put(rootURI, c = new Context(this, makeAbsoluteUrl(rootURI)));
      if (started)
        addHandlerInServer(rootURI, c);

    }
    else if (!(c instanceof Context))
      throw new IllegalStateException("A handler is already registered for: " + rootURI);
    return (Context) c;
  }

  public App addConverter(String name, ResponseConverter<?> conv) {
    converters.put(name, conv);
    reconfigureHandlers();
    return this;
  }

  public ResponseConverter<?> getConverter(String name) {
    return converters.get(name);
  }

  /**
   * Registers all handlers in server and starts the server if not already
   * running.
   */
  public void start() throws IOException {
    if (started)
      throw new IllegalStateException("Already started");
    started = true;

    if (!srv.isStarted())
      srv.start();

    for (Map.Entry<String, RequestHandler> e : handlers.entrySet()) {
      String path = e.getKey();
      if (path.isEmpty())
        path = "/";
      addHandlerInServer(path, e.getValue());
    }
  }

  /**
   * @deprecated The port is now set in the WebServer instance, that may be
   * shared between multiple App instances. Either set the port
   * at App/WebServer creation:
   * <code>new App(new WebServer(port, executor))</code>
   * or set it directly on the server:
   * <code>app.getServer().setPort()</code>
   */
  @Deprecated
  public void setPort(int port) {
    srv.setPort(port);
  }

  public int getPort() {
    return srv.getPort();
  }

  public void stop() {
    srv.removeApp(this);
  }

  /**
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler.
   */
  public JdkApp servePath(String rootURI, String path) {
    return servePath(rootURI, path, null, requireLoggedInByDefault);
  }

  /**
   * Serves the contents of a given path (which may be a directory on the file
   * system or nested in a jar from the classpath) from a given root URI.
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler.
   *
   * @return this
   */
  public JdkApp servePath(String rootURI,
                          String path,
                          ClassLoader loader,
                          boolean requiresAuth) {
    File file = new File(path);
    AbstractResourceHandler h;
    if (file.exists() && file.isDirectory())
      h = new FileHandler(this,
                          mime,
                          makeAbsoluteUrl(rootURI),
                          file,
                          requiresAuth);
    else
      h = new ResourceHandler(this,
                              mime,
                              makeAbsoluteUrl(rootURI),
                              path,
                              loader,
                              requiresAuth);

    handlers.put(rootURI, h);
    if (started)
      addHandlerInServer(rootURI, h);

    return this;
  }

  private void addHandlerInServer(String uri, RequestHandler h) {
    srv.addHandler(makeAbsoluteUrl(uri), h);
  }

  private String makeAbsoluteUrl(String uri) {
    if (rootUrl != null) {
      if (uri.startsWith("/"))
        uri = rootUrl + uri;
      else
        uri = rootUrl + "/" + uri;
    }
    return uri;
  }

  /**
   * WARNING: if rootURI == "/" beware of conflicts with other handlers
   * with root URLs like "/foo": they will conflict with the resource handler.
   */
  public JdkApp serveDir(String rootURI, File dir) {
    return serveDir(rootURI, dir, requireLoggedInByDefault);
  }

  public JdkApp serveDir(String rootURI, File dir, boolean restricted) {
    FileHandler h =
      new FileHandler(this, mime, makeAbsoluteUrl(rootURI), dir, restricted);

    handlers.put(rootURI, h);
    if (started)
      addHandlerInServer(rootURI, h);

    return this;
  }

  public void setContentTypeProvider(ContentTypeProvider mime) {
    this.mime = mime;
  }

  void setThreadLocalRequest(SunRequest req) {
    localRequest.set(req);
  }

  public Request getRequest() {
    return localRequest.get();
  }

  public Response getResponse() {
    return localRequest.get();
  }

  /**
   * Returns true if in DEBUG mode. When in debug mode, server stack traces are
   * sent to clients as body of the 500 response.
   */
  public boolean isDebugEnabled() {
    return Log.DEBUG;
  }

  /**
   * Dumps all registered URLs/methods in a readable way into specified buffer.
   * This can be useful to generate reports or to document an API.
   */
  public StringBuilder dumpRoutes(StringBuilder b) {

    ArrayList<Context> contexts = new ArrayList<>();
    for (RequestHandler h : handlers.values()) {
      if (h instanceof Context)
        contexts.add((Context) h);
    }

    Collections.sort(contexts, new Comparator<Context>() {
      public int compare(Context o1, Context o2) {
        return o1.getRootURI().compareTo(o2.getRootURI());
      }
    });

    for (Context c : contexts) {
      c.dumpUrls(b);
      b.append('\n');
    }

    return b;
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

  /**
   * Returns the login bound with current request (i.e. the one that has been
   * associated with session using {@link #loginUser(String)}.
   *
   * @return current request login, null if none
   */
  public String getCurrentLogin() {
    String token =
      getCookie(((SunRequest) getRequest()).getExchange(), sessionTokenCookie);
    if (token == null)
      return null;

    // method below will fail if we have a token BUT user is unknown or logged out
    return sessionManager.getLogin(token);
  }

  public String makeRandomToken(String login) {
    return (new Random().nextLong() ^ login.hashCode()) + "";
  }

  /**
   * Replies to current request with an HTTP redirect response with specified
   * location.
   */
  public Response redirect(String location) {
    SunRequest r = (SunRequest) getResponse();
    r.addHeader("Location", location);
    r.setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
    return r;
  }

  public void redirect(HttpExchange r, String location) throws IOException {
    r.getResponseHeaders().add("Location", location);
    r.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, 0);
  }

  public Response redirectToLogin() {
    return redirect(loginPage);
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
  public boolean checkLoggedIn(HttpExchange r) throws IOException {
    if (isLoggedIn(r)) {
      return true;
    }
    else {
      if (loginPage != null) {
        if (r.getRequestURI().toString().startsWith(loginPage))
          return true;
        Log.debug("Redirecting to login page: " + r.getRequestURI());
        redirect(r, loginPage + "?url=" + r.getRequestURI());
      }
      else {
        Log.debug("Forbidden (not logged in): " + r.getRequestURI());
        r.sendResponseHeaders(HttpURLConnection.HTTP_FORBIDDEN, -1);
      }
      return false;
    }
  }

  /**
   * Can be called from a request handler to determine whether current
   * session is authenticated.
   */
  public boolean isLoggedIn() {
    HttpExchange r = ((SunRequest) getRequest()).getExchange();
    return isLoggedIn(r);
  }

  private boolean isLoggedIn(HttpExchange r) {
    String token = getCookie(r, sessionTokenCookie);
    return (token != null && sessionManager.isTokenValid(token));
  }

  private String getCookie(HttpExchange r, String name) {
    Headers headers = r.getRequestHeaders();
    if (headers != null) {
      List<String> cookies = headers.get("Cookie");
      if (cookies != null) {
        for (String cookieString : cookies) {
          String[] tokens = cookieString.split("\\s*;\\s*");
          for (String token : tokens) {
            if (token.startsWith(name) && token.charAt(name.length()) == '=') {
              return token.substring(name.length() + 1);
            }
          }
        }
      }
    }
    return null;
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

  /**
   * Returns the default policy for checking whether user must be logged in to
   * access all URLs by default.
   */
  public boolean getRequireLoggedInByDefault() {
    return requireLoggedInByDefault;
  }

  /**
   * Reconfigures existing handlers after a change of configuration (converted
   * added etc.).
   */
  private void reconfigureHandlers() {
    for (MethodHandler h : allHandlers)
      h.configure();
  }

  /**
   * Call this method to destroy the current session, i.e. make the user
   * appearing as "not logged in".
   *
   * @see flak.annotations.LoginRequired
   */
  public void logoutUser() {
    HttpExchange x = ((SunRequest) getRequest()).getExchange();
    String token = getCookie(x, sessionTokenCookie);
    if (token != null)
      sessionManager.removeToken(token);
  }

  public JdkWebServer getServer() {
    return srv;
  }

  /**
   * Adds a handler that will be notified whenever a request is rejected
   */
  public void addErrorHandler(ErrorHandler hook) {
    // force presence of root context to detect unknown URLs
    getContext("/");
    errorHandlers.add(hook);
  }

  /**
   * Adds a handler that will be notified whenever a request is successful
   */
  public void addSuccessHandler(SuccessHandler hook) {
    successHandlers.add(hook);
  }

  void fireError(int status, Request req, Throwable t) {
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

  void on404(SunRequest r) throws IOException {

    if (unknownPageHandler != null)
      unknownPageHandler.handle(r);

    else {

      Log.warn("No handler found for: " + r.getMethod() + " " + r.getRequestURI());

      fireError(404, r, null);

      r.getExchange().sendResponseHeaders(404, 0);
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
