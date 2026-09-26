package flak.backend.jdk;

import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.BeforeHook;
import flak.spi.HandlerSpec;
import flak.spi.SPRequest;
import flak.spi.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Implements flak App with the JDK backend.
 *
 * @author pcdv
 */
public class JdkApp extends AbstractApp {

  private final JdkWebServer srv;

  private final Map<String, Context> handlers = new Hashtable<>();

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
  protected AbstractMethodHandler addHandler(HandlerSpec spec, Object obj) {
    String[] tok = spec.route().split("/+");

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

    return getContext(root.toString()).addHandler(rest.toString(), spec, obj);
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

  @Override
  public Stream<AbstractMethodHandler> getMethodHandlers() {
    return handlers.values().stream().flatMap(c -> c.handlers.stream());
  }

  /**
   * Registers all handlers in server and starts the server if not already
   * running.
   */
  public void start() throws IOException {
    if (started)
      throw new IllegalStateException("Already started");

    // HttpServer only passes on the requests under one of its contexts, i.e.
    // under the static prefix of a route, and answers the others with a 404
    // of its own. A context at the root of the app, even without any route,
    // makes every request under the app ours, so that the unknown page
    // handler and the hooks see them, as with the netty backend.
    // NB: before setting started, which would register it a second time
    getContext("");

    started = true;
    srv.addApp(this);

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

  public JdkWebServer getServer() {
    return srv;
  }

  /**
   * The contexts of this app, i.e. its routes grouped by their static prefix.
   * Not the route handlers, which App.getHandlers() returns.
   */
  public Collection<Context> getContexts() {
    return handlers.values();
  }

}
