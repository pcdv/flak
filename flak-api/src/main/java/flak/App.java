package flak;

import java.io.File;
import java.io.IOException;

/**
 *
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
     * WARNING: if rootURI == "/" beware of conflicts with other handlers with
     * root URLs like "/foo": they will conflict with the resource handler.
     *
     * @return this
     */
  App servePath(String rootURI,
                 String path,
                 ClassLoader loader,
                 boolean restricted);

  App serveDir(String rootURI, File dir);

  App servePath(String rootURI, String path);

  App addOutputFormatter(String name, OutputFormatter<?> outputFormatter);

  App addInputParser(String name, InputParser inputParser);

  void start() throws IOException;

  void stop();

  void addErrorHandler(ErrorHandler handler);

  Request getRequest();

  void loginUser(String login);

  Response redirect(String path);

  void addSuccessHandler(SuccessHandler handler);

  void logoutUser();

  String getCurrentLogin();

  void setRequireLoggedInByDefault(boolean b);

  Response redirectToLogin();

  void setLoginPage(String path);

  Response getResponse();

  void setSessionTokenCookie(String name);

  void setUnknownPageHandler(UnknownPageHandler handler);

  String getRootUrl();

  WebServer getServer();
}
