package flak.spi.util;

import java.util.HashMap;
import java.util.Map;

/**
 * The cookies of a request, read from its Cookie headers as browsers send
 * them: leniently, rather than as RFC 6265 would have them. A value may
 * contain spaces, commas or double quotes, e.g. JSON set by a script, and is
 * returned as sent, quotes included. A cookie without '=' is skipped, and so
 * are all but the first of cookies with the same name, which browsers send
 * with the most specific path first.
 */
public final class Cookies {

  private final Map<String, String> values = new HashMap<>();

  /**
   * @param headers the values of the Cookie headers of the request, usually
   *                only one
   */
  public Cookies(Iterable<String> headers) {
    for (String header : headers) {
      for (String pair : header.split(";")) {
        int eq = pair.indexOf('=');
        if (eq > 0)
          values.putIfAbsent(pair.substring(0, eq).trim(),
                             pair.substring(eq + 1).trim());
      }
    }
  }

  /**
   * The value of specified cookie, null if the request has none.
   */
  public String get(String name) {
    return values.get(name);
  }
}
