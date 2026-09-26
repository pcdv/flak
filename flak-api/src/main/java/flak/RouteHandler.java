package flak;

import java.lang.reflect.Method;
import java.util.List;

/**
 * A route handler of an app, seen from outside: what it is bound to, and the
 * settings that can still be changed once it has been scanned.
 * <p>
 * Annotations can only hardcode a value. This is how an application makes one
 * configurable by its users, e.g. reading the limit of an upload endpoint
 * from its own settings:
 * <pre>
 * app.getHandler("POST", "/api/import").setMaxBodySize(settings.getMaxUpload());
 * </pre>
 *
 * @author pcdv
 * @see App#getHandler(String, String)
 * @see App#getHandlers()
 */
public interface RouteHandler {

  /**
   * The route this handler is bound to, relative to the root of the app, with
   * its variables, e.g. "/db/hello/:name".
   */
  String getRoute();

  /**
   * The HTTP method this handler answers, e.g. "GET" or "POST".
   */
  String getHttpMethod();

  /**
   * The method that is invoked to serve the requests.
   */
  Method getJavaMethod();

  /**
   * The parameters of {@link #getJavaMethod()}, in the same order, with what
   * each one is bound to: a variable of the route, a query parameter, the
   * body, etc.
   */
  List<RouteParameter> getParameters();

  /**
   * Sets the maximum size, in bytes, of the request body this handler
   * accepts, {@link flak.annotations.MaxBodySize#UNLIMITED} for no limit.
   * <p>
   * Takes precedence over {@link flak.annotations.MaxBodySize} on the method
   * and over {@link App#setMaxBodySize(long)}.
   */
  void setMaxBodySize(long maxBodySize);

  /**
   * The maximum body size that applies to this handler: its own if one was
   * set or annotated, that of the app otherwise.
   */
  long getMaxBodySize();
}
