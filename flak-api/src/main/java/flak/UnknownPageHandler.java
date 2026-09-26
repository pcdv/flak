package flak;

import java.io.IOException;

/**
 * Serves the requests that match no route, instead of the default 404.
 * <p>
 * NB: with the JDK backend, the embedded HttpServer only hands a request to
 * the app if its path starts with the static part of one of its routes, and
 * answers the others with its own 404. For this handler to see every unknown
 * URL, the app needs a route at its root, e.g. <code>@Route("/")</code>.
 *
 * @author pcdv
 * @see App#setUnknownPageHandler(UnknownPageHandler)
 */
public interface UnknownPageHandler {

  void handle(Request r) throws IOException;
}
