package flak;

import java.io.IOException;

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

  void addErrorHandler(ErrorHandler handler);

  void addSuccessHandler(SuccessHandler handler);

  Request getRequest();

  Response getResponse();

  void setUnknownPageHandler(UnknownPageHandler handler);

  String getPath();

  String getRootUrl();

  WebServer getServer();

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

  <T extends FlakPlugin> T getPlugin(Class<T> clazz);

  void addPlugin(FlakPlugin plugin);
}
