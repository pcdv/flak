package flak.spi;

/**
 * Looks for a route handler able to serve a request, and runs it. How handlers
 * are organized and matched is up to each backend.
 *
 * @author pcdv
 */
@FunctionalInterface
public interface Dispatcher {

  /**
   * @return true if a handler served the request, false to let the app answer
   * with a 404
   */
  boolean dispatch(SPRequest req) throws Exception;
}
