package flak.spi;

import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * The JFR event recorded for each request, committed by the thread that
 * served it once the response is finished: its start time, duration and
 * thread bound the samples taken while it ran. Profilers rely on its name and
 * fields, which must not change.
 * <p>
 * Disabled by default, so that a recording made with the settings of the JDK
 * (e.g. -XX:StartFlightRecording) does not get one event per request. A
 * recording enables it with:
 * <pre>
 * recording.enable("flak.Request");
 * </pre>
 *
 * @since 3.4.0
 */
@Name(RequestEvent.NAME)
@Label("HTTP Request")
@Category("flak")
@Description("A request served by a flak app")
@Enabled(false)
@StackTrace(false)
public class RequestEvent extends Event {

  public static final String NAME = "flak.Request";

  @Label("App")
  @Description("Path where the app is hosted, empty at the root of the server")
  public String app;

  @Label("Method")
  public String method;

  @Label("Route")
  @Description("Template of the route that matched, null if none did")
  public String route;

  @Label("Path")
  @Description("Path relative to the app, without the query string")
  public String path;

  @Label("Status")
  public int status;

  @Label("Bytes In")
  @Description("Bytes of the request body that were read")
  @DataAmount
  public long bytesIn;

  @Label("Bytes Out")
  @Description("Bytes of the response body that were sent, after compression")
  @DataAmount
  public long bytesOut;
}
