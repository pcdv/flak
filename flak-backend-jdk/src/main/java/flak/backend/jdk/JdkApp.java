package flak.backend.jdk;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
 * Implements flak App with the JDK backend.
 *
 * @author pcdv
 */
public class JdkApp extends AbstractApp {

  private final JdkWebServer srv;

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
    handler.init();

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

  public void stop() {
    srv.removeApp(this);
  }

  /**
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler.
   */
  public JdkApp servePath(String rootURI, String path) {
    return servePath(rootURI, path, null, sessionManager.getRequireLoggedInByDefault());
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
                          String resourcesPath,
                          ClassLoader loader,
                          boolean requiresAuth) {
    File file = new File(resourcesPath);
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
                              resourcesPath,
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
    return serveDir(rootURI, dir, sessionManager.getRequireLoggedInByDefault());
  }

  public JdkApp serveDir(String rootURI, File dir, boolean restricted) {
    FileHandler h =
      new FileHandler(this, mime, makeAbsoluteUrl(rootURI), dir, restricted);

    handlers.put(rootURI, h);
    if (started)
      addHandlerInServer(rootURI, h);

    return this;
  }

  public void setThreadLocalRequest(JdkRequest req) {
    localRequest.set(req);
  }

  public Request getRequest() {
    return localRequest.get();
  }

  public Response getResponse() {
    return localRequest.get();
  }

  /**
   * Reconfigures existing handlers after a change of configuration (converted
   * added etc.).
   */
  protected void reconfigureHandlers() {
    for (MethodHandler h : allHandlers)
      h.configure();
  }

  public JdkWebServer getServer() {
    return srv;
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

}
