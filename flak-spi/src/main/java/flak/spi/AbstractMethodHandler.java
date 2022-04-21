package flak.spi;

import flak.Form;
import flak.InputParser;
import flak.OutputFormatter;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.annotations.Delete;
import flak.annotations.Head;
import flak.annotations.InputFormat;
import flak.annotations.Options;
import flak.annotations.OutputFormat;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.spi.extractor.IntExtractor;
import flak.spi.extractor.ParsedInputExtractor;
import flak.spi.extractor.RequestExtractor;
import flak.spi.extractor.ResponseExtractor;
import flak.spi.extractor.SplatExtractor;
import flak.spi.extractor.StringExtractor;
import flak.spi.parsers.FormParser;
import flak.spi.parsers.QueryParser;
import flak.spi.util.Log;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
@SuppressWarnings("unchecked")
public abstract class AbstractMethodHandler
  implements Comparable<AbstractMethodHandler> {

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
   * The object to invoke method on (i.e. the actual handler).
   */
  protected final Object target;

  @SuppressWarnings("rawtypes")
  protected OutputFormatter outputFormat;

  /**
   * The route as defined with the Route annotation. It is an absolute route
   * for the enclosing app so it may not match the original request path if
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

  public AbstractMethodHandler(AbstractApp app,
                               String path,
                               String[] splitPath,
                               Method m,
                               Object target) {
    this.app = app;
    this.path = path;
    this.splitPath = splitPath;
    this.httpMethod = getHttpMethod(m);
    this.outputFormat = getOutputFormat(m);
    this.javaMethod = m;
    this.target = target;

    // hack for being able to call method even if not public or if the class
    // is not public
    if (!m.isAccessible())
      m.setAccessible(true);

  }

  public void init() {
    if (inputParser == null)
      inputParser = initInputParser();

    extractors = createExtractors(javaMethod);

    if (isNotBasic(javaMethod.getReturnType()) && outputFormat == null) {
      throw new IllegalArgumentException(
        "No @OutputFormat or @JSON around method " + javaMethod.getName() + "()");
    }
  }

  protected ArgExtractor<?>[] createExtractors(Method m) {
    int[] idx = calcIndexes(splitPath);
    Class<?>[] types = m.getParameterTypes();
    ArgExtractor<?>[] extractors = new ArgExtractor[types.length];
    AtomicInteger index = new AtomicInteger();
    for (int i = 0; i < types.length; i++) {
      extractors[i] = createExtractor(m, types[i], i, index, idx);
    }
    if (index.get() < idx.length) {
      throw new IllegalArgumentException("Not enough method parameters");
    }
    return extractors;
  }

  private int[] calcIndexes(String[] tok) {
    int[] res = new int[tok.length];
    int j = 0;
    for (int i = 0; i < tok.length; i++) {
      if (tok[i].charAt(0) == ':') {
        if (splatIndex != -1)
          throw new IllegalArgumentException("Invalid route: " + path);
        res[j++] = i;
      }
      if (tok[i].charAt(0) == '*') {
        if (i != tok.length - 1)
          throw new IllegalArgumentException("Invalid route: " + path);
        res[j++] = i;
        splatIndex = i;
      }
    }
    return Arrays.copyOf(res, j);
  }

  /**
   * @param type the type of argument in method
   * @param i    the extractor's index (i.e. index of argument in method)
   * @param idx  indexes of variables in split URI, eg. { 1 } to extract "world"
   *             from /hello/:name
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected ArgExtractor<?> createExtractor(Method m,
                                            Class<?> type,
                                            int i,
                                            AtomicInteger urlParam,
                                            int[] idx) {
    ArgExtractor<?> ex = app.getCustomExtractor(m, type);
    if (ex != null)
      return ex;

    if (type == Request.class) {
      return new RequestExtractor(i);
    }
    else if (type == Response.class) {
      return new ResponseExtractor(app, i);
    }
    else if (type == String.class) {
      if (urlParam.get() >= idx.length) {
        throw new IllegalArgumentException("Too many method parameters");
      }
      int tokenIndex = idx[urlParam.getAndIncrement()];
      if (splatIndex == tokenIndex)
        return new SplatExtractor(i, tokenIndex);
      else
        return new StringExtractor(i, tokenIndex);
    }
    else if (type == int.class) {
      return new IntExtractor(i, idx[urlParam.getAndIncrement()]);
    }
    else {
      InputParser<?> inputParser;
      if (type == Form.class) {
        inputParser = new FormParser();
      }
      else if (type == Query.class) {
        inputParser = new QueryParser();
      }
      else
        inputParser = this.inputParser;

      if (inputParser == null)
        throw new IllegalArgumentException(
          "No @InputFormat or @JSON found around method " + m.getName() + "()");

      return new ParsedInputExtractor(i, inputParser, type);
    }
  }

  private OutputFormatter<?> getOutputFormat(Method m) {
    OutputFormat output = m.getAnnotation(OutputFormat.class);
    if (output != null) {
      OutputFormatter<?> format = app.getOutputFormatter(output.value());
      if (format == null)
        throw new IllegalArgumentException("In method " + m.getName() + ": unknown output format: " + output
                                                                                                        .value());
      return format;
    }

    return null;
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

  public void setInputParser(InputParser<?> inputParser) {
    this.inputParser = inputParser;
  }

  public static boolean isNotBasic(Class<?> type) {
    return type != String.class && type != byte[].class && type != InputStream.class && type != Response.class && type != void.class;
  }

  private InputParser<?> initInputParser() {
    InputFormat input = javaMethod.getAnnotation(InputFormat.class);
    if (input != null)
      return app.getInputParser(input.value());
    return null;
  }

  public static String getHttpMethod(Method m) {
    if (m.getAnnotation(Post.class) != null)
      return "POST";
    if (m.getAnnotation(Put.class) != null)
      return "PUT";
    if (m.getAnnotation(Patch.class) != null)
      return "PATCH";
    if (m.getAnnotation(Head.class) != null)
      return "HEAD";
    if (m.getAnnotation(Delete.class) != null)
      return "DELETE";
    if (m.getAnnotation(Options.class) != null)
      return "OPTIONS";
    return "GET";
  }

  public Object execute(SPRequest req) throws Exception {
    req.setHandler(javaMethod);

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

  public AbstractApp getApp() {
    return app;
  }

  public InputParser<?> getInputParser() {
    return inputParser;
  }
}
