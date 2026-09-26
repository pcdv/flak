package flak;

import java.io.IOException;

/**
 * Serves the requests, under the path of the app, that match no route,
 * instead of the default 404.
 *
 * @author pcdv
 * @see App#setUnknownPageHandler(UnknownPageHandler)
 */
public interface UnknownPageHandler {

  void handle(Request r) throws IOException;
}
