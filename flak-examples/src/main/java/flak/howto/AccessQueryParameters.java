package flak.howto;

import flak.App;
import flak.Request;
import flak.annotations.Route;

/**
 * Example of how the query string can be parsed.
 *
 * @author pcdv
 */
public class AccessQueryParameters {

  private final App app;

  public AccessQueryParameters(App app) {
    this.app = app;
  }

  @Route("/api/stuff")
  public String getStuff() {
    Request req = app.getRequest();
    String param1 = req.getArg("param1", null);
    return "You submitted param1=" + param1;
  }
}
