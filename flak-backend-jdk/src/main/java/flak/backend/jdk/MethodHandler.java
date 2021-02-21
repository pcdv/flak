package flak.backend.jdk;

import flak.Response;
import flak.spi.AbstractMethodHandler;
import flak.spi.SPRequest;
import flak.spi.util.IO;
import flak.spi.util.Log;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
public class MethodHandler extends AbstractMethodHandler {

  private static final String[] EMPTY = {};

  MethodHandler(Context ctx, String uri, Method m, Object target) {
    super(ctx.app,
          ctx.getRootURI() + uri,
          uri.isEmpty() ? EMPTY : uri.substring(1).split("/"),
          m,
          target);
  }

  /**
   * Checks whether current handler should respond to specified request.
   */
  protected boolean isApplicable(SPRequest req) {

    Log.debug("isApplicable ? " + req.getPath() + " vs " + getRoute());

    if (!req.getMethod().equals(getHttpMethod()))
      return false;

    String[] uri = req.getSplitUri();
    String[] tok = splitPath;
    if (uri.length != tok.length && splatIndex == -1)
      return false;

    if (uri.length <= splatIndex)
      return false;

    for (int i = 0; i < tok.length; i++) {
      if (tok[i].charAt(0) != ':' && tok[i].charAt(0) != '*' && !tok[i].equals(uri[i]))
        return false;
    }

    return true;
  }

  @SuppressWarnings({"StatementWithEmptyBody", "unchecked"})
  protected void processResponse(Response r, Object res) throws Exception {
    if (outputFormat != null) {
      outputFormat.convert(res, r);
    }
    else if (res instanceof Response) {
      // do nothing: status and headers should already be set
    }
    else if (res instanceof String) {
      r.setStatus(HttpURLConnection.HTTP_OK);
      r.getOutputStream().write(((String) res).getBytes(StandardCharsets.UTF_8));
    }
    else if (res instanceof byte[]) {
      r.setStatus(HttpURLConnection.HTTP_OK);
      r.getOutputStream().write((byte[]) res);
    }
    else if (res instanceof InputStream) {
      r.setStatus(HttpURLConnection.HTTP_OK);
      IO.pipe((InputStream) res, r.getOutputStream(), false);
    }
    else if (res == null) {
      if (!r.isStatusSet())
        r.setStatus(200);
    }
    else
      throw new RuntimeException("Unexpected return value: " + res + " from " + javaMethod
        .toGenericString());

  }


}
