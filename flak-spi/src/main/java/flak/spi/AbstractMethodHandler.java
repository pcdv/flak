package flak.spi;

import flak.BeforeHook;
import flak.Form;
import flak.InputParser;
import flak.OutputFormatter;
import flak.Query;
import flak.RouteHandler;
import flak.RouteParameter;
import flak.Request;
import flak.Response;
import flak.spi.extractor.IntExtractor;
import flak.spi.extractor.ParsedInputExtractor;
import flak.spi.extractor.RequestExtractor;
import flak.spi.extractor.ResponseExtractor;
import flak.spi.extractor.SplatExtractor;
import flak.spi.extractor.StringExtractor;
import flak.spi.parsers.FormParser;
import flak.spi.parsers.QueryParser;
import flak.spi.util.IO;
import flak.spi.util.Log;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
@SuppressWarnings("unchecked")
public abstract class AbstractMethodHandler
  implements RouteHandler, Comparable<AbstractMethodHandler> {

  public final AbstractApp app;

  /**
   * The split URI, starting at the first path variable, eg. [ ":name", "foo" ] for
   * "/hello/:name/foo"
   */
  protected final String[] splitPath;

  /**
   * The HTTP method (GET, POST, ...)
   */
  protected final String httpMethod;

  /**
   * The method to invoke to process requests.
   */
  protected final Method javaMethod;

  /**
   * What each parameter of the method is bound to.
   */
  private final List<RouteParameter> parameters;

  /**
   * The object to invoke method on (i.e. the actual handler).
   */
  protected final Object target;
  protected final boolean allowCompress;

  @SuppressWarnings("rawtypes")
  protected OutputFormatter outputFormat;

  /**
   * The route as defined with the Route annotation. It is an absolute route
   * for the enclosing app, so it may not match the original request path if
   * several apps share a given web server.
   */
  protected final String path;

  /**
   * These will extract from the request all the arguments that must be passed
   * to the target method.
   */
  private ArgExtractor<?>[] extractors;

  private final List<BeforeHook> beforeHooks = new Vector<>();

  protected InputParser<?> inputParser;

  protected int splatIndex = -1;

  /**
   * The limit declared by @MaxBodySize on the method or its class, or set
   * with setMaxBodySize(), and null when the handler does not care and the
   * app decides. Applied per request rather than resolved once, so that
   * setMaxBodySize() on the app still counts when it is called after the
   * handlers have been scanned.
   */
  private Long maxBodySize;

  /**
   * See {@link #setFallback(boolean)}.
   */
  private volatile boolean fallback;

  /**
   * @param path      the route, as the backend sees it
   * @param splitPath the part of the route the handler matches, split, e.g.
   *                  [ ":name", "foo" ]
   * @param spec      what the handler is made of
   */
  public AbstractMethodHandler(AbstractApp app,
                               String path,
                               String[] splitPath,
                               HandlerSpec spec,
                               Object target) {
    this.app = app;
    this.path = path;
    this.splitPath = splitPath;
    this.httpMethod = spec.httpMethod();
    this.outputFormat = spec.outputFormatter();
    this.inputParser = spec.inputParser();
    this.allowCompress = spec.compress();
    this.javaMethod = spec.javaMethod();
    this.parameters = spec.parameters();
    this.target = target;
    this.maxBodySize = spec.maxBodySize();

    // hack for being able to call method even if not public or if the class
    // is not public
    javaMethod.setAccessible(true);
  }

  public void init() {
    extractors = createExtractors();

    if (isNotBasic(javaMethod.getReturnType()) && outputFormat == null) {
      throw new IllegalArgumentException(
        "No @OutputFormat or @JSON around method " + javaMethod.getName() + "()");
    }
  }

  protected ArgExtractor<?>[] createExtractors() {
    Map<String, Integer> tokens = variableTokens();
    ArgExtractor<?>[] extractors = new ArgExtractor[parameters.size()];
    for (int i = 0; i < extractors.length; i++) {
      extractors[i] = createExtractor(parameters.get(i), i, tokens);
    }
    return extractors;
  }

  /**
   * The index of each variable in the split path, by name, e.g. { name: 0 }
   * for [ ":name", "foo" ].
   */
  private Map<String, Integer> variableTokens() {
    Map<String, Integer> res = new HashMap<>();
    for (int i = 0; i < splitPath.length; i++) {
      char c = splitPath[i].charAt(0);
      if (c == ':' || c == '*') {
        // nothing after the splat, which takes the rest of the path
        if (splatIndex != -1 || (c == '*' && i != splitPath.length - 1))
          throw new IllegalArgumentException("Invalid route: " + path);
        if (c == '*')
          splatIndex = i;
        if (res.put(splitPath[i].substring(1), i) != null)
          throw new IllegalArgumentException("Invalid route, a variable is repeated: " + path);
      }
    }
    return res;
  }

  /**
   * @param param  the method parameter, and what it is bound to
   * @param i      the extractor's index (i.e. index of argument in method)
   * @param tokens the index of each variable in the split path
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected ArgExtractor<?> createExtractor(RouteParameter param,
                                            int i,
                                            Map<String, Integer> tokens) {
    Class<?> type = param.type();

    switch (param.kind()) {
      case QUERY:
        return QueryExtractor.from(param, i);

      case PATH:
        Integer token = tokens.get(param.name());
        if (token == null)
          throw new IllegalArgumentException("No variable " + param.name() + " in route " + path);
        if (type == int.class)
          return new IntExtractor(i, token);
        if (token == splatIndex)
          return new SplatExtractor(i, path);
        return new StringExtractor(i, token);

      case BODY:
        if (type == Form.class)
          return new ParsedInputExtractor(i, new FormParser(), type);
        if (inputParser == null)
          throw new IllegalArgumentException(
            "No @InputFormat or @JSON found around method " + javaMethod.getName() + "()");
        return new ParsedInputExtractor(i, inputParser, type);

      default:
        ArgExtractor<?> ex = app.getCustomExtractor(javaMethod, type);
        if (ex != null)
          return ex;
        if (type == Request.class)
          return new RequestExtractor(i);
        if (type == Response.class)
          return new ResponseExtractor(app, i);
        if (type == Query.class)
          return new ParsedInputExtractor(i, new QueryParser(), type);
        throw new IllegalArgumentException("Cannot bind parameter " + param.javaParameter()
                                           + " of method " + javaMethod.getName() + "()");
    }
  }

  public void addHook(BeforeHook hook) {
    beforeHooks.add(hook);
  }

  public Object getTarget() {
    return target;
  }

  public void setOutputFormatter(OutputFormatter<?> outputFormatter) {
    this.outputFormat = outputFormatter;
  }

  /**
   * What converts the values returned by the method, null if it returns one
   * of the basic types.
   */
  public OutputFormatter<?> getOutputFormatter() {
    return outputFormat;
  }

  public void setInputParser(InputParser<?> inputParser) {
    this.inputParser = inputParser;
  }

  public static boolean isNotBasic(Class<?> type) {
    return type != String.class && type != byte[].class && type != InputStream.class && type != Response.class && type != void.class;
  }

  @Override
  public void setMaxBodySize(long maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  @Override
  public long getMaxBodySize() {
    return maxBodySize == null ? app.getMaxBodySize() : maxBodySize;
  }

  /**
   * Makes this handler serve a request only if no other handler of the same
   * route would, whatever the order in which they were registered. This is
   * how the index of static resources served at "/" leaves the app's own "/"
   * route, if any, in charge. Every backend must honor it when dispatching.
   */
  public void setFallback(boolean fallback) {
    this.fallback = fallback;
  }

  public boolean isFallback() {
    return fallback;
  }

  public Object execute(SPRequest req) throws Exception {
    req.setHandler(javaMethod);
    req.setMaxBodySize(getMaxBodySize());

    for (BeforeHook hook : beforeHooks) {
      hook.execute(req);
    }

    Object[] args = extractArgs(req);

    if (Log.DEBUG)
      Log.debug(String.format("Invoking %s.%s%s",
                              target.getClass().getSimpleName(),
                              javaMethod.getName(),
                              Arrays.toString(args)));

    Object res = javaMethod.invoke(target, args);

    app.fireSuccess(req, javaMethod, args, res);
    return res;
  }

  /**
   * Computes the list of arguments to pass to the decorated method.
   */
  protected Object[] extractArgs(SPRequest r) throws Exception {
    Object[] args = new Object[extractors.length];
    for (int i = 0; i < args.length; i++) {
      args[i] = extractors[i].extract(r);
    }
    return args;
  }

  public int compareTo(AbstractMethodHandler o) {
    if (Objects.equals(path, o.path))
      return httpMethod.compareTo(o.httpMethod);
    return path.compareTo(o.path);
  }

  public String getRoute() {
    return path;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public Method getJavaMethod() {
    return javaMethod;
  }

  @Override
  public List<RouteParameter> getParameters() {
    return parameters;
  }

  public AbstractApp getApp() {
    return app;
  }

  public InputParser<?> getInputParser() {
    return inputParser;
  }

  /**
   * Checks whether current handler should respond to specified request.
   */
  public boolean isApplicable(SPRequest req) {

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
  public void processResponse(SPResponse r, Object res) throws Exception {
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
        setOkUnlessSet(r);
        if (((String) res).length() > CompressionHelper.COMPRESS_THRESHOLD)
          out = CompressionHelper.maybeCompress(r);
        out.write(((String) res).getBytes(StandardCharsets.UTF_8));
      }
      else if (res instanceof byte[]) {
        setOkUnlessSet(r);
        if (((byte[]) res).length > CompressionHelper.COMPRESS_THRESHOLD)
          out = CompressionHelper.maybeCompress(r);
        out.write((byte[]) res);
      }
      else if (res instanceof InputStream) {
        setOkUnlessSet(r);
        out = CompressionHelper.maybeCompress(r);
        InputStream input = (InputStream) res;
        try {
          IO.pipe(input, out, false);
        }
        catch (Exception e) {
          Log.error("Broken stream while serving " + javaMethod, e);
          r.abort();
          close(input);
        }
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

  /**
   * A body goes out with 200, unless the handler chose another status, e.g. 201
   * for a resource it created.
   */
  private static void setOkUnlessSet(SPResponse r) {
    if (!r.isStatusSet())
      r.setStatus(HttpURLConnection.HTTP_OK);
  }

  private static void close(Closeable c) {
    try {
      c.close();
    }
    catch (Exception ignored) {
    }
  }
}
