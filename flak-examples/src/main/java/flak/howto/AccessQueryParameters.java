package flak.howto;

import flak.Request;
import flak.annotations.Route;

/**
 * Example of how the query string can be parsed.
 */
public class AccessQueryParameters {

  @Route("/api/stuff")
  public String getStuff(Request req) {
    return "You submitted param1=" + req.getQuery().get("param1");
  }
}
