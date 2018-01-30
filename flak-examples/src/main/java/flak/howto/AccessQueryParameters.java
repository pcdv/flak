package flak.howto;

import flak.Request;
import flak.annotations.Route;

/**
 * Example of how the query string can be parsed.
 *
 * @author pcdv
 */
public class AccessQueryParameters {

  @Route("/api/stuff")
  public String getStuff(Request req) {
    return "You submitted param1=" + req.getArg("param1", null);
  }
}
