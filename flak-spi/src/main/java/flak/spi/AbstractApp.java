package flak.spi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Stream;

import flak.*;
import flak.annotations.Route;
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
    for (Method method : obj.getClass().getMethods()) {
      Route route = method.getAnnotation(Route.class);
      if (route != null) {
        try {
          addHandler0(route.value(), method, obj);
        }
        catch (Exception e) {
          throw new ScanException("Error while scanning " + method, e);
        }
      }
    }
    return this;
  }

  public void addHandler0(String route, Method method, Object obj) {
    AbstractMethodHandler handler = addHandler(route, method, obj);
    for (SPPlugin plugin : plugins) {
      plugin.preInit(handler);
    }
    handler.init();
  }

  protected abstract AbstractMethodHandler addHandler(String route,
                                                      Method method,
                                                      Object obj);

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

  @SuppressWarnings("unused")
  public ArgExtractor<?> getCustomExtractor(Method m, Class<?> type) {
    return extractors.get(type);
  }

  public <T> void addCustomExtractor(Class<T> type, ArgExtractor<T> extractor) {
    extractors.put(type, extractor);
  }

  public void addPlugin(FlakPlugin plugin) {
    plugins.add((SPPlugin) plugin);

    getMethodHandlers().forEach(((SPPlugin) plugin)::preInit);
  }

  protected abstract Stream<AbstractMethodHandler> getMethodHandlers();

  @SuppressWarnings("unchecked")
  @Override
  public <T extends FlakPlugin> T getPlugin(Class<T> clazz) {
    return (T) plugins.stream()
                      .filter(p -> p.getClass() == clazz)
                      .findFirst()
                      .get();
  }
}
