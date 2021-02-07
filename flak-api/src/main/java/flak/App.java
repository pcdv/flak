package flak;

import java.io.IOException;

/**
 * @author pcdv
 */
public interface App {

  /**
   * Scans specified object for route handlers, i.e. public methods with @Route
   * annotation.
   *
   * @see flak.annotations.Route
   */
  App scan(Object obj);

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

  <T> T getPlugin(Class<T> clazz);
}
