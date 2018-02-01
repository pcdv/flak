package flak.backend.jdk;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import flak.ContentTypeProvider;
import flak.ErrorHandler;
import flak.Request;
import flak.RequestHandler;
import flak.Response;
import flak.annotations.Route;
import flak.backend.jdk.resource.AbstractResourceHandler;
import flak.backend.jdk.resource.FileHandler;
import flak.backend.jdk.resource.ResourceHandler;
import flak.spi.AbstractApp;
import flak.util.Log;

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
public class JdkApp extends AbstractApp {

  protected final JdkWebServer srv;

  private final ThreadLocal<JdkRequest> localRequest = new ThreadLocal<>();

  private List<MethodHandler> allHandlers = new ArrayList<>(256);

  private boolean started;

  JdkApp(JdkWebServer server) {
    this(null, server);
  }

  JdkApp(String rootUrl, JdkWebServer server) {
    super(rootUrl);
    this.srv = server;

    // in case we are extended by a subclass with annotations
    scan(this);
  }

  @Override
  protected void addHandler(Route route, Method m, Object obj) {
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

    MethodHandler handler =
      getContext(root.toString()).addHandler(rest.toString(), m, obj);

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

  void setThreadLocalRequest(JdkRequest req) {
    localRequest.set(req);
  }

  public Request getRequest() {
    return localRequest.get();
  }

  public Response getResponse() {
    return localRequest.get();
  }

  /**
   * Returns the login bound with current request (i.e. the one that has been
   * associated with session using {@link #loginUser(String)}.
   *
   * @return current request login, null if none
   */
  public String getCurrentLogin() {
    String token =
      getCookie(((JdkRequest) getRequest()).getExchange(), sessionTokenCookie);
    if (token == null)
      return null;

    // method below will fail if we have a token BUT user is unknown or logged out
    return sessionManager.getLogin(token);
  }

  /**
   * Replies to current request with an HTTP redirect response with specified
   * location.
   */
  public Response redirect(String location) {
    JdkRequest r = (JdkRequest) getResponse();
    r.addHeader("Location", location);
    r.setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
    return r;
  }

  public void redirect(HttpExchange r, String location) throws IOException {
    r.getResponseHeaders().add("Location", location);
    r.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, 0);
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
    HttpExchange r = ((JdkRequest) getRequest()).getExchange();
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
  protected void reconfigureHandlers() {
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
    HttpExchange x = ((JdkRequest) getRequest()).getExchange();
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
    super.addErrorHandler(hook);
  }

  void on404(JdkRequest r) throws IOException {

    if (unknownPageHandler != null)
      unknownPageHandler.handle(r);

    else {

      Log.warn("No handler found for: " + r.getMethod() + " " + r.getPath());

      fireError(404, r, null);

      r.getExchange().sendResponseHeaders(404, 0);
    }
  }

  public Collection<RequestHandler> getHandlers() {
    return handlers.values();
  }

  public String relativePath(String path) {
    if (this.rootUrl == null)
      return path;
    else
      return path.substring(rootUrl.length());
  }
}
