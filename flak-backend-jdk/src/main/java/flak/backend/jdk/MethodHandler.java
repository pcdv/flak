package flak.backend.jdk;

import flak.Response;
import flak.annotations.Compress;
import flak.spi.AbstractMethodHandler;
import flak.spi.CompressionHelper;
import flak.spi.SPRequest;
import flak.spi.util.IO;
import flak.spi.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
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
    if (allowCompress)
      r.setCompressionAllowed(true);
    if (outputFormat != null) {
      outputFormat.convert(res, r);
    }
    else if (res instanceof Response) {
      // do nothing: status and headers should already be set
    }
    else {
      OutputStream out = r.getOutputStream();
      if (res instanceof String) {
        r.setStatus(HttpURLConnection.HTTP_OK);
        if (((String) res).length() > Compress.COMPRESS_THRESHOLD)
          out = CompressionHelper.maybeCompress(r);
        out.write(((String) res).getBytes(StandardCharsets.UTF_8));
      }
      else if (res instanceof byte[]) {
        r.setStatus(HttpURLConnection.HTTP_OK);
        if (((byte[]) res).length > Compress.COMPRESS_THRESHOLD)
          out = CompressionHelper.maybeCompress(r);
        out.write((byte[]) res);
      }
      else if (res instanceof InputStream) {
        r.setStatus(HttpURLConnection.HTTP_OK);
        out = CompressionHelper.maybeCompress(r);
        IO.pipe((InputStream) res, out, false);
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
}
