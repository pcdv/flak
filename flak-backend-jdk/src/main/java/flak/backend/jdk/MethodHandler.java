package flak.backend.jdk;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import flak.Form;
import flak.InputParser;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.backend.jdk.extractor.IntExtractor;
import flak.backend.jdk.extractor.ParsedInputExtractor;
import flak.backend.jdk.extractor.RequestExtractor;
import flak.backend.jdk.extractor.ResponseExtractor;
import flak.backend.jdk.extractor.SplatExtractor;
import flak.backend.jdk.extractor.StringExtractor;
import flak.spi.AbstractMethodHandler;
import flak.spi.ArgExtractor;
import flak.spi.SPRequest;
import flak.util.Log;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
@SuppressWarnings("unchecked")
public class MethodHandler extends AbstractMethodHandler {

  private static final String[] EMPTY = {};

  private final String uri;

  /**
   * The split URI, eg. { "hello", ":name" }
   */
  private final String[] splitPath;

  private int splat = -1;

  MethodHandler(Context ctx, String uri, Method m, Object target) {
    super(ctx.app, ctx.getRootURI() + uri, m, target);

    this.uri = uri;
    this.splitPath = uri.isEmpty() ? EMPTY : uri.substring(1).split("/");
  }

  protected ArgExtractor[] createExtractors(Method m) {
    int[] idx = calcIndexes(splitPath);
    Class<?>[] types = m.getParameterTypes();
    ArgExtractor[] extractors = new ArgExtractor[types.length];
    AtomicInteger index = new AtomicInteger();
    for (int i = 0; i < types.length; i++) {
      extractors[i] = createExtractor(m, types[i], i, index, idx);
    }
    return extractors;
  }

  private int[] calcIndexes(String[] tok) {
    int[] res = new int[tok.length];
    int j = 0;
    for (int i = 0; i < tok.length; i++) {
      if (tok[i].charAt(0) == ':') {
        if (splat != -1)
          throw new IllegalArgumentException("Invalid route: " + uri);
        res[j++] = i;
      }
      if (tok[i].charAt(0) == '*') {
        if (i != tok.length - 1)
          throw new IllegalArgumentException("Invalid route: " + uri);
        res[j++] = i;
        splat = i;
      }
    }
    return Arrays.copyOf(res, j);
  }

  /**
   * @param type the type of argument in method
   * @param i the extractor's index (i.e. index of argument in method)
   * @param idx indexes of variables in split URI, eg. { 1 } to extract "world"
   * from
   */
  protected ArgExtractor createExtractor(Method m,
                                         Class<?> type,
                                         int i,
                                         AtomicInteger urlParam,
                                         int[] idx) {
    ArgExtractor ex = app.getCustomExtractor(m, type);
    if (ex != null)
      return ex;

    if (type == Request.class) {
      return new RequestExtractor(i);
    }
    else if (type == Response.class) {
      return new ResponseExtractor(app, i);
    }
    else if (type == String.class) {
      int tokenIndex = idx[urlParam.getAndIncrement()];
      if (splat == tokenIndex)
        return new SplatExtractor(i, tokenIndex);
      else
        return new StringExtractor(i, tokenIndex);
    }
    else if (type == int.class) {
      return new IntExtractor(i, idx[urlParam.getAndIncrement()]);
    }
    else {
      InputParser inputParser;
      if (type == Form.class) {
        inputParser = new FormParser();
      }
      else if (type == Query.class) {
        inputParser = new QueryParser();
      }
      else
        inputParser = getInputParser(m, app);

      if (inputParser == null)
        throw new IllegalArgumentException(
          "No @InputFormat or @JSON found around method " + m.getName() + "()");

      return new ParsedInputExtractor(i, inputParser, type);
    }
  }

  private boolean hasSplat() {
    return splat != -1;
  }

  private String[] getSplitPath() {
    return splitPath;
  }


  /**
   * Checks whether current handler should respond to specified request.
   */
  @Override
  protected boolean isApplicable(SPRequest req) {

    Log.debug("isApplicable ? " + req.getPath() + " vs " + getRoute());

    if (!req.getHttpMethod().equals(getHttpMethod()))
      return false;

    String[] uri = req.getSplitUri();
    String[] tok = getSplitPath();
    if (uri.length != tok.length) {
      if (!hasSplat() || uri.length < tok.length)
        return false;
    }

    for (int i = 0; i < tok.length; i++) {
      if (tok[i].charAt(0) != ':' && tok[i].charAt(0) != '*' && !tok[i].equals(
        uri[i]))
        return false;
    }

    return true;
  }

}
