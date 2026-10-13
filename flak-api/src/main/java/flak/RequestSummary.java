package flak;

/**
 * What came out of a request, as passed to
 * {@link RequestListener#onEnd(Request, RequestSummary)}.
 *
 * @since 3.4.0
 */
public interface RequestSummary {

  /**
   * The template of the route that served the request, e.g. "/user/:id", or
   * null when it matched no route.
   */
  String getRoute();

  /**
   * The status of the response.
   */
  int getStatus();

  /**
   * The number of bytes of the request body that were read, by the handler or
   * by flak on its behalf. A body left unread is not counted.
   */
  long getBytesIn();

  /**
   * The number of bytes of the response body that were sent, after
   * compression. Headers are not counted.
   */
  long getBytesOut();

  /**
   * When flak started serving the request, as a {@link System#nanoTime()}.
   */
  long getStartNanos();

  /**
   * How long it took to serve the request, from the start to the moment the
   * response was finished.
   */
  long getDurationNanos();
}
