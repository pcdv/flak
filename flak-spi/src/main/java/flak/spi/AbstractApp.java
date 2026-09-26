package flak.spi;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import flak.*;
import flak.annotations.Route;
import flak.spi.resource.AbstractResourceHandler;
import flak.spi.resource.FileHandler;
import flak.spi.resource.ResourceHandler;
import flak.spi.util.Log;

public abstract class AbstractApp implements App {

  /**
   * Optional URL where the app is plugged.
   */
  protected final String rootUrl;

  private final Map<String, OutputFormatter<?>> outputFormatterMap =
    new Hashtable<>();

  private final Map<String, InputParser<?>> inputParserMap = new Hashtable<>();

  private final Vector<ErrorHandler> errorHandlers = new Vector<>();

  private final Vector<SuccessHandler> successHandlers = new Vector<>();

  protected UnknownPageHandler unknownPageHandler;

  private final Map<Class<?>, ArgExtractor<?>> extractors = new HashMap<>();

  private final List<SPPlugin> plugins = new ArrayList<>();

  private final List<BeforeHook> beforeHooks = new Vector<>();

  private final ThreadLocal<Request> localRequest = new ThreadLocal<>();

  private long maxBodySize = DEFAULT_MAX_BODY_SIZE;

  public AbstractApp(String rootUrl) {
    this.rootUrl = rootUrl;
  }

  public String getRootUrl() {
    WebServer srv = getServer();
    return srv.getProtocol() + "://" + srv.getHostName() + ":" + srv.getPort() + getPath();
  }

  public String getPath() {
    return rootUrl == null ? "" : rootUrl;
  }

  /**
   * Scans specified object for route handlers, i.e. public methods with @Route
   * annotation.
   *
   * @see Route
   */
  public App scan(Object obj) {
    return scan(obj, "");
  }

  public App scan(Object obj, String prefix) {
    for (Method method : obj.getClass().getMethods()) {
      Route route = method.getAnnotation(Route.class);
      if (route != null) {
        try {
          addHandler0(prefix + route.value(), method, obj);
        }
        catch (Exception e) {
          throw new ScanException("Error while scanning " + method, e);
        }
      }
    }
    return this;
  }

  @Override
  public App serveDir(String path, File dir) {
    return serveDir(path, dir, new ResourceOptions());
  }

  @Override
  public App serveDir(String path, File dir, ResourceOptions options) {
    // NB: it may not exist yet, e.g. a cache created on demand
    if (dir.exists() && !dir.isDirectory())
      throw new IllegalArgumentException("Not a directory: " + dir);
    return serve(path, new FileHandler(options.getContentTypes(),
                                       path,
                                       dir,
                                       options.isRestricted()),
                 options);
  }

  @Override
  public App serveClasspath(String path, String resourcePath) {
    return serveClasspath(path, resourcePath, new ResourceOptions());
  }

  @Override
  public App serveClasspath(String path, String resourcePath, ResourceOptions options) {
    return serve(path, new ResourceHandler(options.getContentTypes(),
                                           path,
                                           resourcePath,
                                           options.getClassLoader(),
                                           options.isRestricted()),
                 options);
  }

  private App serve(String path, AbstractResourceHandler h, ResourceOptions options) {
    if (options.isRestricted() && plugins.stream().noneMatch(SPPlugin::enforcesRestrictions))
      throw new IllegalStateException("Restricted resources require a plugin checking logins, "
                                      + "e.g. flak-login: without it, " + path
                                      + " would be open to anyone");
    try {
      addHandler0(path + "/*splat",
                  h.getClass().getMethod("doGet", Request.class, String.class),
                  h);

      // the root itself, which the splat does not match. A fallback, so that
      // a route of the app at the same path, e.g. "/", stays in charge
      String root = path.length() > 1 && path.endsWith("/")
        ? path.substring(0, path.length() - 1)
        : path;
      addHandler0(root.isEmpty() ? "/" : root,
                  h.getClass().getMethod("serveRoot", Request.class),
                  h)
        .setFallback(true);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
    return this;
  }

  /**
   * Adds a handler for specified method, described by its Flak annotations,
   * e.g. @Post, @QueryParam.
   */
  public AbstractMethodHandler addHandler0(String route, Method method, Object obj) {
    return addHandler0(FlakAnnotations.read(this, route, method), obj);
  }

  /**
   * Adds a handler described by specified spec, which need not come from
   * Flak's annotations.
   */
  public AbstractMethodHandler addHandler0(HandlerSpec spec, Object obj) {
    AbstractMethodHandler handler = addHandler(spec, obj);
    for (SPPlugin plugin : plugins) {
      plugin.preInit(handler);
    }
    handler.init();
    return handler;
  }

  /**
   * Creates the handler and registers it in the backend.
   */
  protected abstract AbstractMethodHandler addHandler(HandlerSpec spec, Object obj);

  public App addOutputFormatter(String name, OutputFormatter<?> conv) {
    outputFormatterMap.put(name, conv);
    return this;
  }

  public OutputFormatter<?> getOutputFormatter(String name) {
    return outputFormatterMap.get(name);
  }

  @Override
  public App addInputParser(String name, InputParser<?> inputParser) {
    inputParserMap.put(name, inputParser);
    return this;
  }

  @SuppressWarnings("unchecked")
  public <T> InputParser<T> getInputParser(String name) {
    return (InputParser<T>) inputParserMap.get(name);
  }

  public String makeAbsoluteUrl(String uri) {
    if (rootUrl != null) {
      if (uri.startsWith("/"))
        uri = rootUrl + uri;
      else
        uri = rootUrl + "/" + uri;
    }
    if (uri == null || uri.isEmpty())
      return "/";

    return uri;
  }

  /**
   * Returns true if in DEBUG mode. When in debug mode, server stack traces are
   * sent to clients as body of the 500 response.
   */
  public boolean isDebugEnabled() {
    return Log.DEBUG;
  }

  /**
   * Adds a handler that will be notified whenever a route handler fails with
   * an exception (500). Neither a 404 nor an HttpException is reported.
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

  public boolean fireError(int status, Request req, Throwable t) {
    if (errorHandlers.isEmpty())
      return false;

    for (ErrorHandler errorHandler : errorHandlers) {
      try {
        errorHandler.onError(status, req, t);
      }
      catch (Exception e) {
        Log.error(e, e);
      }
    }

    return true;
  }

  public void fireSuccess(SPRequest req,
                          Method method,
                          Object[] args,
                          Object res) {
    for (SuccessHandler successHandler : successHandlers) {
      successHandler.onSuccess(req, method, args, res);
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

  public String relativePath(String path) {
    return rootUrl == null || rootUrl.equals("/") ? path
                                                  : path.substring(rootUrl.length());
  }

  @Override
  public String absolutePath(String path) {
    return rootUrl == null || rootUrl.equals("/") ? path : rootUrl + path;
  }

  private static final Pattern URL_WITH_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

  /**
   * The Location header of a redirect to specified target: a path, relative
   * to the app, or a URL, with a scheme ("https://...") or without one
   * ("//host/..."), which is kept as is.
   */
  public String redirectLocation(String target) {
    if (target.startsWith("//") || URL_WITH_SCHEME.matcher(target).find())
      return target;
    return absolutePath(target);
  }

  @SuppressWarnings("unused")
  public ArgExtractor<?> getCustomExtractor(Method m, Class<?> type) {
    return extractors.get(type);
  }

  public <T> void addCustomExtractor(Class<T> type, ArgExtractor<T> extractor) {
    extractors.put(type, extractor);
  }

  @Override
  public <T> void addCustomExtractor(Class<T> type, CustomExtractor<T> extractor) {
    // the public API only exposes the request, the index is meaningless here
    addCustomExtractor(type, new ArgExtractor<T>(-1) {
      @Override
      public T extract(SPRequest request) throws Exception {
        return extractor.extract(request);
      }
    });
  }

  @Override
  public void setMaxBodySize(long maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  @Override
  public long getMaxBodySize() {
    return maxBodySize;
  }

  public void addPlugin(FlakPlugin plugin) {
    SPPlugin p = (SPPlugin) plugin;
    plugins.add(p);

    // NB: install() before preInit(), so that a handler is never passed to a
    // plugin that is not fully set up yet
    p.install();

    getMethodHandlers().forEach(p::preInit);
  }

  /**
   * All the route handlers registered in this app, in no particular order.
   * Useful to document or introspect an API, e.g. to dump the routes or to
   * generate an OpenAPI specification.
   */
  public abstract Stream<AbstractMethodHandler> getMethodHandlers();

  @Override
  public Stream<RouteHandler> getHandlers() {
    return getMethodHandlers().map(h -> h);
  }

  @Override
  public RouteHandler getHandler(String httpMethod, String route) {
    String method = httpMethod.toUpperCase();

    return getMethodHandlers().filter(h -> h.getHttpMethod().equals(method)
                                           && h.getRoute().equals(route))
                              .findFirst()
                              .orElseThrow(() -> new NoSuchElementException(
                                "No handler for " + method + " " + route +
                                ". Routes of this app answering " + method + ": " +
                                routesFor(method)));
  }

  private String routesFor(String httpMethod) {
    String routes = getMethodHandlers().filter(h -> h.getHttpMethod().equals(httpMethod))
                                       .map(AbstractMethodHandler::getRoute)
                                       .sorted()
                                       .collect(Collectors.joining(", "));
    return routes.isEmpty() ? "none" : routes;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends FlakPlugin> T getPlugin(Class<T> clazz) {
    return (T) plugins.stream()
                      .filter(p -> p.getClass() == clazz)
                      .findFirst()
                      .orElseThrow(() -> new NoSuchElementException(
                        "Plugin " + clazz.getName() + " is not installed in this app"));
  }

  /**
   * Serves a request: runs the before hooks, lets the backend find and run a
   * handler, then deals with a 404 or with whatever the handler threw.
   *
   * @param req        the request, which is also the response
   * @param dispatcher finds and runs the handler, backend specific
   */
  public void handle(SPRequest req, Dispatcher dispatcher) throws IOException {
    setThreadLocalRequest(req);
    SPResponse resp = (SPResponse) req.getResponse();

    try {
      onBefore(req);

      if (!dispatcher.dispatch(req))
        on404(req);
    }
    catch (Throwable t) {

      if (t instanceof BeforeHook.StopProcessingException) {
        return;
      }

      if (t instanceof InvocationTargetException) {
        t = ((InvocationTargetException) t).getTargetException();
      }

      if (t instanceof HttpException) {
        resp.setStatus(((HttpException) t).getResponseCode());
        resp.getOutputStream().write(t.getMessage().getBytes(StandardCharsets.UTF_8));
        resp.addHeader("Content-Type", "text/plain");
        return;
      }

      if (!fireError(500, req, t))
        Log.error(t, t);

      if (!resp.isStatusSet())
        resp.setStatus(500);

      if (isDebugEnabled() && !resp.hasOutputStream()) {
        t.printStackTrace(new PrintStream(resp.getOutputStream()));
      }
      else if (!resp.hasOutputStream()) {
        // a body, however terse, so that clients can tell an error response
        // from an empty one. The cause is logged, not sent to the client
        resp.addHeader("Content-Type", "text/plain");
        resp.getOutputStream()
            .write("Internal Server Error".getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  /**
   * Called when no handler matched the request.
   */
  protected void on404(SPRequest r) throws IOException {
    SPResponse resp = (SPResponse) r.getResponse();

    if (unknownPageHandler != null)
      unknownPageHandler.handle(r);

    else {
      Log.warn("No handler found for: " + r.getMethod() + " " + r.getPath());

      // NB: 404 is no longer reported to ErrorHandler

      if (!resp.isStatusSet())
        resp.setStatus(404);

      if (!resp.hasOutputStream()) {
        resp.addHeader("Content-Type", "text/plain");
        resp.getOutputStream().write("Not found".getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  public void setThreadLocalRequest(Request req) {
    localRequest.set(req);
  }

  @Override
  public Request getRequest() {
    return localRequest.get();
  }

  public Response getResponse() {
    return getRequest().getResponse();
  }

  @Override
  public void addBeforeAllHook(BeforeHook hook) {
    beforeHooks.add(hook);
  }

  protected void onBefore(SPRequest request) throws BeforeHook.StopProcessingException {
    for (BeforeHook hook : beforeHooks) {
      hook.execute(request);
    }
  }
}
