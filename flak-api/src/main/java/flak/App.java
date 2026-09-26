package flak;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * @author pcdv
 */
public interface App {

  /**
   * The maximum size of a request body when nothing else is specified. Kept
   * deliberately modest: a handler that accepts more says so explicitly, with
   * {@link flak.annotations.MaxBodySize}.
   */
  long DEFAULT_MAX_BODY_SIZE = 16L * 1024 * 1024;

  /**
   * Scans specified object for route handlers, i.e. public methods with @Route
   * annotation.
   *
   * @see flak.annotations.Route
   */
  App scan(Object obj);

  /**
   * Same as {@link #scan(Object)}, binding the routes of the object under
   * specified prefix, e.g. "/v2": a same class can serve several paths.
   */
  App scan(Object obj, String prefix);

  /**
   * Adds a formatter that takes the value returned by the route handler and
   * writes it into the response. The method must be decorated with {@link
   * flak.annotations.OutputFormat}.
   */
  App addOutputFormatter(String name, OutputFormatter<?> outputFormatter);

  /**
   * Adds a parser that can read the request's input and convert it to the
   * type of an argument of the route handler. The method must be decorated
   * with {@link flak.annotations.InputFormat}.
   */
  App addInputParser(String name, InputParser<?> inputParser);

  <T> InputParser<T> getInputParser(String name);

  /**
   * Allows route handlers to accept an argument of specified type: the
   * extractor is called for each request to build the value of the argument.
   *
   * @param type the type of the route handler argument
   * @param extractor builds the value of the argument from the request
   */
  <T> void addCustomExtractor(Class<T> type, CustomExtractor<T> extractor);

  /**
   * Starts the application.
   */
  void start() throws IOException;

  /**
   * Stops the application.
   */
  void stop();

  /**
   * Adds a handler notified whenever a route handler fails with an
   * exception, i.e. with a 500.
   */
  void addErrorHandler(ErrorHandler handler);

  /**
   * Adds a handler notified whenever a route handler returns normally.
   */
  void addSuccessHandler(SuccessHandler handler);

  /**
   * Returns the request being served by the current thread, so that code
   * called by a route handler can reach it without it being passed around.
   */
  Request getRequest();

  /**
   * Returns the response of the request being served by the current thread.
   */
  Response getResponse();

  /**
   * Sets the handler serving the requests that match no route, instead of
   * the default 404.
   */
  void setUnknownPageHandler(UnknownPageHandler handler);

  /**
   * Returns the path at which the app is hosted, e.g. "/app1", or an empty
   * string for an app at the root of the server.
   */
  String getPath();

  /**
   * Returns the URL of the app, e.g. "http://localhost:8080/app1", built
   * from the host name of the server, see {@link WebServer#setHostName}.
   */
  String getRootUrl();

  WebServer getServer();

  /**
   * Prepends the path of the app to specified path, e.g. "/foo" becomes
   * "/app1/foo".
   */
  String absolutePath(String path);

  /**
   * Sets the maximum size, in bytes, of the request body accepted by the route
   * handlers of this app, {@link flak.annotations.MaxBodySize#UNLIMITED} for
   * no limit. A handler can override it with
   * {@link flak.annotations.MaxBodySize}.
   * <p>
   * Defaults to {@link #DEFAULT_MAX_BODY_SIZE}.
   */
  void setMaxBodySize(long maxBodySize);

  long getMaxBodySize();

  /**
   * All the route handlers scanned by this app, in no particular order.
   * Useful to introspect an API, or to configure the handlers in bulk.
   */
  Stream<RouteHandler> getHandlers();

  /**
   * Returns the handler bound to specified route, so that it can be
   * configured at runtime, e.g.
   * <pre>
   * app.getHandler("POST", "/api/import").setMaxBodySize(maxUpload);
   * </pre>
   *
   * @param httpMethod e.g. "POST"
   * @param route the route, relative to the root of the app, exactly as it
   * was declared, variables included, e.g. "/db/hello/:name"
   * @throws java.util.NoSuchElementException if no handler is bound to it: a
   * route that is configured but does not exist is a mistake worth reporting
   * rather than ignoring
   */
  RouteHandler getHandler(String httpMethod, String route);

  /**
   * Returns the plugin of specified class installed in this app, e.g.
   * <code>app.getPlugin(FlakLogin.class)</code>.
   *
   * @throws java.util.NoSuchElementException if it is not installed
   */
  <T extends FlakPlugin> T getPlugin(Class<T> clazz);

  /**
   * Installs a plugin by hand, typically one of the application's own, which
   * no FlakPluginLoader can discover.
   */
  void addPlugin(FlakPlugin plugin);
}
