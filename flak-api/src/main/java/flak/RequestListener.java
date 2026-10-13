package flak;

/**
 * Notified at the beginning and at the end of each request served by an app,
 * on the thread that serves it. Meant for monitoring: counting requests per
 * route, timing them, starting a profiling session before a handler runs.
 * <p>
 * An exception thrown by a listener is logged, and does not affect the
 * request.
 *
 * @see App#addRequestListener(RequestListener)
 * @since 3.4.0
 */
public interface RequestListener {

  /**
   * Called once the request matched a route, before its handler runs, and
   * before the hooks of the handler (e.g. the login check). Not called for a
   * request that matches no route, nor for one rejected by a
   * {@link BeforeHook} added with {@link App#addBeforeAllHook}, since those run
   * before routing.
   *
   * @param request the request, whose {@link Request#getRoute()} is known
   */
  default void onBegin(Request request) {}

  /**
   * Called for every request, matched or not, once its response is finished,
   * i.e. sent or at least handed over to the network.
   *
   * @param request the request
   * @param summary what came out of it: route, status, size, duration
   */
  default void onEnd(Request request, RequestSummary summary) {}
}
