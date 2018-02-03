package flak.backend.jdk;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import flak.Form;
import flak.InputParser;
import flak.OutputFormatter;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.annotations.Delete;
import flak.annotations.InputFormat;
import flak.annotations.JSON;
import flak.annotations.LoginNotRequired;
import flak.annotations.LoginPage;
import flak.annotations.LoginRequired;
import flak.annotations.OutputFormat;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.backend.jdk.extractor.ArgExtractor;
import flak.backend.jdk.extractor.IntExtractor;
import flak.backend.jdk.extractor.ParsedInputExtractor;
import flak.backend.jdk.extractor.RequestExtractor;
import flak.backend.jdk.extractor.ResponseExtractor;
import flak.backend.jdk.extractor.SplatExtractor;
import flak.backend.jdk.extractor.StringExtractor;
import flak.util.IO;
import flak.util.Log;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
@SuppressWarnings("unchecked")
public class MethodHandler implements Comparable<MethodHandler> {

  /**
   * The HTTP method
   */
  private final String httpMethod;

  /**
   * The method to invoke to process requests.
   */
  private final Method javaMethod;

  /**
   * The object to invoke method on.
   */
  private final Object target;

  /**
   * The split URI, eg. { "hello", ":name" }
   */
  private final String[] tok;

  private final OutputFormatter outputFormat;

  private boolean loginRequired;

  private int splat = -1;

  private final Context ctx;

  private final String uri;

  private static final String[] EMPTY = {};

  private final ArgExtractor[] extractors;

  public MethodHandler(Context ctx, String uri, Method m, Object target) {
    this.ctx = ctx;
    this.uri = uri;
    this.httpMethod = getHttpMethod(m);
    this.outputFormat = getOutputFormat(m);
    this.javaMethod = m;
    this.target = target;
    this.tok = uri.isEmpty() ? EMPTY : uri.substring(1).split("/");

    if (m.getAnnotation(LoginPage.class) != null)
      ctx.app.setLoginPage(ctx.getRootURI() + uri);

    configure();

    // hack for being able to call method even if not public or if the class
    // is not public
    if (!m.isAccessible())
      m.setAccessible(true);

    extractors = createExtractors(m);

    if (!isBasic(m.getReturnType()) && outputFormat == null) {
      throw new IllegalArgumentException(
        "No @OutputFormat or @JSON around method " + m.getName() + "()");
    }
  }

  private ArgExtractor[] createExtractors(Method m) {
    int[] idx = calcIndexes(tok);
    Class<?>[] types = m.getParameterTypes();
    ArgExtractor[] extractors = new ArgExtractor[types.length];
    AtomicInteger index = new AtomicInteger();
    for (int i = 0; i < types.length; i++) {
      extractors[i] = createExtractor(m, types[i], i, index, idx);
    }
    return extractors;
  }

  /**
   * @param type the type of argument in method
   * @param i the extractor's index (i.e. index of argument in method)
   * @param idx indexes of variables in split URI, eg. { 1 } to extract "world"
   * from
   */
  private ArgExtractor createExtractor(Method m,
                                       Class<?> type,
                                       int i,
                                       AtomicInteger urlParam,
                                       int[] idx) {
    if (type == Request.class) {
      return new RequestExtractor(i);
    }
    else if (type == Response.class) {
      return new ResponseExtractor(ctx.app, i);
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
        inputParser = getInputParser(m, ctx.app);

      if (inputParser == null)
        throw new IllegalArgumentException(
          "No @InputFormat or @JSON found around method " + m.getName() + "()");

      return new ParsedInputExtractor(i, inputParser, type);
    }
  }

  private OutputFormatter<?> getOutputFormat(Method m) {
    OutputFormat output = m.getAnnotation(OutputFormat.class);
    if (output != null) {
      OutputFormatter<?> format = ctx.app.getOutputFormatter(output.value());
      if (format == null)
        throw new IllegalArgumentException("In method " + m.getName() + ": unknown output format: " + output
                                                                                                        .value());
      return format;
    }

    JSON json = m.getAnnotation(JSON.class);
    if (json != null) {
      OutputFormatter<?> fmt = ctx.app.getOutputFormatter("JSON");
      if (fmt == null)
        throw new IllegalArgumentException("In method " + m.getName() + ": no OutputFormatter with name JSON was declared");
      return fmt;
    }

    return null;
  }

  private static boolean isBasic(Class<?> type) {
    return type == String.class || type == byte[].class || type == InputStream.class || type == Response.class || type == void.class;
  }

  private InputParser getInputParser(Method m, JdkApp app) {
    InputFormat input = m.getAnnotation(InputFormat.class);
    if (input != null)
      return app.getInputParser(input.value());
    JSON json = m.getAnnotation(JSON.class);
    if (json != null) {
      if (!isBasic(m.getReturnType())) {
        return ctx.app.getInputParser("JSON");
      }
    }
    return null;
  }

  private static String getHttpMethod(Method m) {
    if (m.getAnnotation(Post.class) != null)
      return "POST";
    if (m.getAnnotation(Put.class) != null)
      return "PUT";
    if (m.getAnnotation(Patch.class) != null)
      return "PATCH";
    if (m.getAnnotation(Delete.class) != null)
      return "DELETE";
    return m.getAnnotation(Route.class).method();
  }

  /**
   * Called during construction and when App configuration changes that may
   * require adaptation in handlers.
   */
  public void configure() {
    loginRequired = isLoginRequired();
  }

  private boolean isLoginRequired() {
    if (javaMethod.getAnnotation(LoginRequired.class) != null)
      return true;

    return javaMethod.getAnnotation(LoginNotRequired.class) == null && //
             javaMethod.getAnnotation(LoginPage.class) == null && //
             ctx.app.getRequireLoggedInByDefault();
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
   * @return true when request was handled, false when it was ignored (eg. not
   * applicable)
   */
  @SuppressWarnings("unchecked")
  public boolean handle(JdkRequest req) throws Exception {

    if (!isApplicable(req))
      return false;

    if (loginRequired && !ctx.app.checkLoggedIn(req.getExchange())) {
      return true;
    }

    Object[] args = extractArgs(req);

    if (Log.DEBUG)
      Log.debug(String.format("Invoking %s.%s%s",
                              target.getClass().getSimpleName(),
                              javaMethod.getName(),
                              Arrays.toString(args)));

    Object res = javaMethod.invoke(target, args);

    ctx.app.fireSuccess(javaMethod, args, res);

    return processResponse(req, res);
  }

  @SuppressWarnings("StatementWithEmptyBody")
  private boolean processResponse(JdkRequest r, Object res) throws Exception {
    if (outputFormat != null) {
      outputFormat.convert(res, r);
    }
    else if (res instanceof Response) {
      // do nothing: status and headers should already be set
    }
    else if (res instanceof String) {
      r.setStatus(HttpURLConnection.HTTP_OK);
      r.getOutputStream().write(((String) res).getBytes("UTF-8"));
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
      try {
        // will throw if already set
        r.setStatus(200);
      }
      catch (Exception ignored) {
      }
      r.getOutputStream().close();
    }
    else
      throw new RuntimeException("Unexpected return value: " + res + " from " + javaMethod
                                                                                  .toGenericString());

    return true;
  }

  /**
   * Computes the list of arguments to pass to the decorated method.
   */
  private Object[] extractArgs(JdkRequest r) throws Exception {
    Object[] args = new Object[extractors.length];
    for (int i = 0; i < args.length; i++) {
      args[i] = extractors[i].extract(r);
    }
    return args;
  }

  /**
   * Checks whether current handler should respond to specified request.
   */
  private boolean isApplicable(JdkRequest r) {
    if (!r.getHttpMethod().equals(this.httpMethod))
      return false;

    String[] uri = r.split;
    if (uri.length != tok.length) {
      if (splat == -1 || uri.length < tok.length)
        return false;
    }

    for (int i = 0; i < tok.length; i++) {
      if (tok[i].charAt(0) != ':' && tok[i].charAt(0) != '*' && !tok[i].equals(
        uri[i]))
        return false;
    }

    return true;
  }

  public int compareTo(MethodHandler o) {
    if (Arrays.equals(tok, o.tok))
      return httpMethod.compareTo(o.httpMethod);
    return uri.compareTo(o.uri);
  }

  public String getURI() {
    return uri;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public Method getJavaMethod() {
    return javaMethod;
  }

}
