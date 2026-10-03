package flak.util;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import flak.BeforeHook;
import flak.Request;
import flak.Response;

/**
 * Lets pages of other origins call the routes of an app from a browser, with
 * <code>fetch()</code> or <code>XMLHttpRequest</code>: answers the preflight
 * requests of browsers, and adds the headers of CORS to the responses sent to
 * allowed origins. Added to an app as a hook that runs before every request:
 * <pre>
 * app.addBeforeAllHook(new Cors().allowOrigins("https://ui.example.com"));
 * </pre>
 * Preflight requests are answered before routing and before the hooks of the
 * handlers, so that flak-login does not reject them: browsers send them
 * without credentials. They never reach the handlers, @Options ones
 * included.
 * <p>
 * CORS does not restrict who calls the app: browsers merely hide the response
 * from the page when the origin is not allowed. A request from such an origin
 * is served as usual, without the headers of CORS.
 *
 * @since 3.2.0
 */
public class Cors implements BeforeHook {

  private final Set<String> origins = new LinkedHashSet<>();

  private boolean anyOrigin;

  private List<String> methods =
    List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

  /**
   * Null to allow whatever headers a preflight request asks for.
   */
  private String headers;

  private String exposedHeaders;

  private boolean credentials;

  private Duration maxAge = Duration.ofMinutes(10);

  /**
   * Allows specified origins, e.g. "https://ui.example.com": a scheme, a host
   * and a port unless it is the default one, as browsers send them in the
   * Origin header.
   */
  public Cors allowOrigins(String... origins) {
    this.origins.addAll(Arrays.asList(origins));
    return this;
  }

  /**
   * Allows any origin. Rather list them when the routes rely on cookies,
   * since any site could then use those of its visitors.
   */
  public Cors allowAnyOrigin() {
    this.anyOrigin = true;
    return this;
  }

  /**
   * The methods pages may use, by default GET, HEAD, POST, PUT, PATCH and
   * DELETE.
   */
  public Cors allowMethods(String... methods) {
    this.methods = List.of(methods);
    return this;
  }

  /**
   * The headers pages may send, e.g. "Content-Type", "Authorization". By
   * default, whatever a preflight request asks for.
   */
  public Cors allowHeaders(String... headers) {
    this.headers = String.join(", ", headers);
    return this;
  }

  /**
   * The headers of responses that pages may read, besides the few that
   * browsers always let them, e.g. Content-Type.
   */
  public Cors exposeHeaders(String... headers) {
    this.exposedHeaders = String.join(", ", headers);
    return this;
  }

  /**
   * Whether pages may send credentials, i.e. cookies, with
   * <code>fetch(url, {credentials: "include"})</code>. The response then
   * names the origin rather than "*", which browsers refuse with
   * credentials.
   * <p>
   * Browsers only send a cookie to another site, e.g. from app.example.org to
   * api.example.com, when it is <code>SameSite=None; Secure</code>, and some
   * never do. Hosts of a same domain are a same site.
   */
  public Cors allowCredentials(boolean credentials) {
    this.credentials = credentials;
    return this;
  }

  /**
   * How long browsers may keep the answer to a preflight request, 10 minutes
   * by default. Browsers cap it, e.g. Chrome at 2 hours.
   */
  public Cors maxAge(Duration maxAge) {
    this.maxAge = maxAge;
    return this;
  }

  @Override
  public void execute(Request req) throws StopProcessingException {
    String origin = req.getHeader("Origin");
    if (origin == null)
      return;

    Response resp = req.getResponse();
    String requestedMethod = req.getHeader("Access-Control-Request-Method");
    boolean preflight = "OPTIONS".equals(req.getMethod()) && requestedMethod != null;
    boolean allowed = anyOrigin || origins.contains(origin);

    // the answer depends on the origin, and must not be cached for another
    if (!anyOrigin || credentials)
      resp.addHeader("Vary", "Origin");

    if (preflight) {
      if (!allowed || !methods.contains(requestedMethod)) {
        resp.setStatus(403);
        throw STOP;
      }
      allow(resp, origin);
      resp.addHeader("Access-Control-Allow-Methods", String.join(", ", methods));
      String requestedHeaders = req.getHeader("Access-Control-Request-Headers");
      String allowedHeaders = headers != null ? headers : requestedHeaders;
      if (allowedHeaders != null && !allowedHeaders.isEmpty())
        resp.addHeader("Access-Control-Allow-Headers", allowedHeaders);
      resp.addHeader("Access-Control-Max-Age", String.valueOf(maxAge.toSeconds()));
      resp.setStatus(204);
      throw STOP;
    }

    if (allowed) {
      allow(resp, origin);
      if (exposedHeaders != null)
        resp.addHeader("Access-Control-Expose-Headers", exposedHeaders);
    }
  }

  private void allow(Response resp, String origin) {
    resp.addHeader("Access-Control-Allow-Origin",
                   anyOrigin && !credentials ? "*" : origin);
    if (credentials)
      resp.addHeader("Access-Control-Allow-Credentials", "true");
  }
}
