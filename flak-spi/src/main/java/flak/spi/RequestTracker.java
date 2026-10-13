package flak.spi;

import flak.RequestSummary;

/**
 * Follows a request from the moment a backend starts serving it to the moment
 * its response is finished, then tells the listeners of the app and records
 * the JFR event. A backend creates one per request, as early as it can, and
 * passes it to {@link AbstractApp#fireEnd} once the response is finished.
 *
 * @since 3.4.0
 */
public class RequestTracker implements RequestSummary {

  private final long startNanos = System.nanoTime();

  private final RequestEvent event = new RequestEvent();

  private long durationNanos;
  private String route;
  private int status;
  private long bytesIn;
  private long bytesOut;

  public RequestTracker() {
    event.begin();
  }

  /**
   * Takes the measures once the response is finished.
   */
  void end(AbstractApp app, SPRequest req) {
    durationNanos = System.nanoTime() - startNanos;
    event.end();

    SPResponse resp = (SPResponse) req.getResponse();
    route = req.getRoute();
    status = resp.getStatus();
    bytesIn = req.getBytesIn();
    bytesOut = resp.getBytesOut();

    if (event.shouldCommit()) {
      event.app = app.getPath();
      event.method = req.getMethod();
      event.route = route;
      event.path = req.getPath();
      event.status = status;
      event.bytesIn = bytesIn;
      event.bytesOut = bytesOut;
      event.commit();
    }
  }

  @Override
  public String getRoute() {
    return route;
  }

  @Override
  public int getStatus() {
    return status;
  }

  @Override
  public long getBytesIn() {
    return bytesIn;
  }

  @Override
  public long getBytesOut() {
    return bytesOut;
  }

  @Override
  public long getStartNanos() {
    return startNanos;
  }

  @Override
  public long getDurationNanos() {
    return durationNanos;
  }

  @Override
  public String toString() {
    return route + " " + status + " in=" + bytesIn + " out=" + bytesOut +
           " " + durationNanos / 1000 + "us";
  }
}
