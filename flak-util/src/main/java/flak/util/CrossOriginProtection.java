package flak.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import flak.BeforeHook;
import flak.HttpException;
import flak.Request;

/**
 * Protects an app against cross-site request forgery (CSRF): refuses with 403
 * the requests that pages of other origins make browsers send, except with
 * safe methods (GET, HEAD, OPTIONS). Added to an app as a hook that runs before
 * every request:
 * <pre>
 * app.addBeforeAllHook(new CrossOriginProtection());
 * </pre>
 * Browsers state where a request comes from in the Sec-Fetch-Site header,
 * which pages cannot forge, or in the Origin header for older ones. Clients
 * that send neither, such as scripts and HTTP libraries, are not browsers
 * acting for a user, and are let through. So are the pages of the app itself,
 * which need no token. Handlers must then change nothing on a safe method.
 * <p>
 * Works like <code>http.CrossOriginProtection</code> of Go 1.25.
 *
 * @since 3.3.0
 */
public class CrossOriginProtection implements BeforeHook {

  private final Set<String> origins = new LinkedHashSet<>();

  /**
   * Lets specified origins send any request, e.g. "https://ui.example.com":
   * a scheme, a host and a port unless it is the default one, as browsers send
   * them in the Origin header. Typically those allowed by {@link Cors}.
   */
  public CrossOriginProtection trustOrigins(String... origins) {
    this.origins.addAll(Arrays.asList(origins));
    return this;
  }

  @Override
  public void execute(Request req) {
    if (!isAllowed(req))
      throw new HttpException(403, "Cross-origin request refused");
  }

  private boolean isAllowed(Request req) {
    switch (req.getMethod()) {
      case "GET":
      case "HEAD":
      case "OPTIONS":
        return true;
    }

    String origin = req.getHeader("Origin");
    if (origin != null && origins.contains(origin))
      return true;

    String site = req.getHeader("Sec-Fetch-Site");
    if (site != null)
      // "none" when the user typed the URL or opened a bookmark
      return site.equals("same-origin") || site.equals("none");

    // a browser too old to send Sec-Fetch-Site, or not a browser
    if (origin == null)
      return true;

    // the origin's host and port, compared with the Host header
    int scheme = origin.indexOf("://");
    String host = req.getHeader("Host");
    return scheme > 0 && host != null
           && origin.substring(scheme + 3).toLowerCase(Locale.ROOT)
                    .equals(host.toLowerCase(Locale.ROOT));
  }
}
