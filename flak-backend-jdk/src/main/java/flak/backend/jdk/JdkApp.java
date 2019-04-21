package flak.backend.jdk;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import flak.spi.PluginUtil;
import flak.Request;
import flak.Response;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.util.Log;

/**
 * Implements flak App with the JDK backend.
 *
 * @author pcdv
 */
public class JdkApp extends AbstractApp {

  private final JdkWebServer srv;

  private final ThreadLocal<JdkRequest> localRequest = new ThreadLocal<>();

  private final Map<String, Context> handlers = new Hashtable<>();

  private boolean started;

  JdkApp(JdkWebServer server) {
    this(null, server);
  }

  JdkApp(String rootUrl, JdkWebServer server) {
    super(rootUrl);
    this.srv = server;

    PluginUtil.loadPlugins(this);

    // in case we are extended by a subclass with annotations
    scan(this);
  }

  @Override
  protected AbstractMethodHandler addHandler(String route,
                                             Method m,
                                             Object obj) {
    String[] tok = route.split("/+");

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

    return getContext(root.toString()).addHandler(rest.toString(), m, obj);
  }

  /**
   * Gets or creates a Context for specified root URI.
   */
  private Context getContext(String rootURI) {
    Context c = handlers.get(rootURI);

    if (c == null) {
      Log.debug("Creating context for " + rootURI);
      String absPath = makeAbsoluteUrl(rootURI);
      handlers.put(rootURI, c = new Context(this, absPath));
      if (started)
        addHandlerInServer(rootURI, c);

    }

    return c;
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

    for (Map.Entry<String, Context> e : handlers.entrySet()) {
      String path = e.getKey();
      if (path.isEmpty())
        path = "/";
      addHandlerInServer(path, e.getValue());
    }
  }

  public void stop() {
    srv.removeApp(this);
  }

  private void addHandlerInServer(String uri, Context h) {
    srv.addHandler(makeAbsoluteUrl(uri), h);
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

  public Collection<Context> getHandlers() {
    return handlers.values();
  }

}
