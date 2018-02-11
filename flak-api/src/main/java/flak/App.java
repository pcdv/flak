package flak;

import java.io.File;
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
   * Serves the contents of a given path (which may be a directory on the file
   * system or nested in a jar from the classpath) from a given root URI.
   * <p>
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler. Prefer
   * using separate URI paths, like "/api", "/static" etc.
   *
   * @param rootURI the path at which resources will be accessible from clients
   * @param resourcesPath the actual path of resources on server
   * @param loader the class loader that will be used to find resources
   * (optional but may be required if resources are not accessible from default
   * class loader)
   * @param restricted indicates whether users must be logged in to access
   * resources
   * @return this
   */
  App servePath(String rootURI,
                String resourcesPath,
                ClassLoader loader,
                boolean restricted);

  /**
   * Same as {@link #servePath(String, String, ClassLoader, boolean)} with
   * default class loader and no logged in restriction.
   */
  App servePath(String rootURI, String path);

  /**
   * Same as {@link #servePath(String, String, ClassLoader, boolean)} except
   * that only a local directory is supported.
   */
  App serveDir(String rootURI, File dir);

  /**
   * Adds a formatter that takes the value returned by the route handler and
   * writes it into the response. The method must be decorated with {@link
   * flak.annotations.OutputFormat}. Note that the {@link
   * flak.annotations.JSON} annotation implies the presence of an
   * OutputFormatter with name "JSON".
   */
  App addOutputFormatter(String name, OutputFormatter<?> outputFormatter);

  /**
   * Adds a parser that can read the request's input and convert it to the
   * type of an argument of the route handler. The method must be decorated
   * with {@link flak.annotations.InputFormat}. Note that the {@link
   * flak.annotations.JSON} annotation implies the presence of an InputParser
   * with name "JSON".
   */
  App addInputParser(String name, InputParser inputParser);

  InputParser getInputParser(String name);

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

  Response redirect(String path);

  Response getResponse();

  void setUnknownPageHandler(UnknownPageHandler handler);

  String getPath();

  String getRootUrl();

  WebServer getServer();

  SessionManager getSessionManager();

  String absolutePath(String path);
}
