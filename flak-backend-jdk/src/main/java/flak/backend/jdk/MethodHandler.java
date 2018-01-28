package flak.backend.jdk;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import flak.InputParser;
import flak.OutputFormatter;
import flak.Request;
import flak.Response;
import flak.annotations.Delete;
import flak.annotations.InputFormat;
import flak.annotations.LoginNotRequired;
import flak.annotations.LoginPage;
import flak.annotations.LoginRequired;
import flak.annotations.OutputFormat;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.backend.jdk.extractor.ArgExtractor;
import flak.backend.jdk.extractor.InputExtractor;
import flak.backend.jdk.extractor.RequestExtractor;
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

  private final String outputFormatName;

  private final int argc;

  private boolean loginRequired;

  private int splat = -1;

  private final String rootURI;

  private final Context ctx;

  @SuppressWarnings("rawtypes")
  private OutputFormatter outputFormatter;

  private final String uri;

  private static final String[] EMPTY = {};

  private ArgExtractor[] extractors;

  public MethodHandler(Context ctx, String uri, Method method, Object target) {
    this.ctx = ctx;
    this.uri = uri;
    this.rootURI = uri;
    this.httpMethod = getHttpMethod(method);
    this.outputFormatName = getOutputFormat(method);
    this.javaMethod = method;
    this.target = target;
    this.tok = uri.isEmpty() ? EMPTY : uri.substring(1).split("/");

    /*
    The indexes of variables in split URI, eg. { 1 } to extract "world" from
    "/hello/world" if URI schema is "/hello/:name"
    */
    int[] idx = calcIndexes(tok);
    this.argc = method.getParameterTypes().length;

    if (method.getAnnotation(LoginPage.class) != null)
      ctx.app.setLoginPage(ctx.getRootURI() + uri);

    configure();

    // hack for being able to call method even if not public or if the class
    // is not public
    if (!method.isAccessible())
      method.setAccessible(true);

    Class<?>[] types = method.getParameterTypes();
    extractors = new ArgExtractor[types.length];
    int index = 0;
    for (int i = 0; i < types.length; i++) {
      if (types[i] == Request.class) {
        extractors[i] = new RequestExtractor(i);
      }
      else if (types[i] == String.class) {
        // TODO: handle splat case
        int tokenIndex = idx[index++];
        if (splat == tokenIndex)
          extractors[i] = new SplatExtractor(i, tokenIndex);
        else
          extractors[i] = new StringExtractor(i, tokenIndex);
      }
      else {
        if (i != types.length - 1)
          throw new IllegalArgumentException(
            "Only the last argument of method can be another type than String in " + method);

        InputFormat inputFormat = method.getAnnotation(InputFormat.class);

        if (inputFormat == null)
          throw new IllegalArgumentException(
            "No @InputFormat specified in method " + method);

        if (!inputFormat.type().isAssignableFrom(types[i]))
          throw new IllegalArgumentException //
                  ("Last argument is not of type " + inputFormat.type()
                                                                .getName() + " in " + method);
        extractors[i] =
          new InputExtractor(i, getInputParser(method, ctx.app), types[i]);
      }
    }
  }

  private static String getOutputFormat(Method m) {
    OutputFormat c = m.getAnnotation(OutputFormat.class);
    if (c != null)
      return c.value();

    Route route = m.getAnnotation(Route.class);
    if (route.outputFormat().isEmpty())
      return null;
    return route.outputFormat();
  }

  private static InputParser getInputParser(Method m, JdkApp app) {
    InputFormat annotation = m.getAnnotation(InputFormat.class);
    return annotation == null ? null : app.getInputParser(annotation.name());
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

    if (this.outputFormatter == null && outputFormatName != null)
      this.outputFormatter = ctx.app.getOutputFormatter(outputFormatName);
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
          throw new IllegalArgumentException("Invalid route: " + rootURI);
        res[j++] = i;
      }
      if (tok[i].charAt(0) == '*') {
        if (i != tok.length - 1)
          throw new IllegalArgumentException("Invalid route: " + rootURI);
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

  private boolean processResponse(JdkRequest r, Object res) throws Exception {
    if (outputFormatter != null) {
      outputFormatter.convert(res, r);
    }
    else if (outputFormatName != null) {
      throw new IllegalStateException("Converter '" + outputFormatName + "' not registered in App.");
    }
    else if (res instanceof Response) {
      // do nothing: status and headers should already be set
    }
    else if (res instanceof String) {
      r.setStatus(200);
      r.getOutputStream().write(((String) res).getBytes("UTF-8"));
    }
    else if (res instanceof byte[]) {
      r.setStatus(200);
      r.getOutputStream().write((byte[]) res);
    }
    else if (res instanceof InputStream) {
      r.setStatus(200);
      IO.pipe((InputStream) res, r.getOutputStream(), false);
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
    Object[] args = new Object[argc];
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
