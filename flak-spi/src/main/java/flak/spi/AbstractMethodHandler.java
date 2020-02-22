package flak.spi;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import flak.InputParser;
import flak.OutputFormatter;
import flak.Response;
import flak.annotations.Delete;
import flak.annotations.Head;
import flak.annotations.InputFormat;
import flak.annotations.OutputFormat;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.spi.util.IO;
import flak.spi.util.Log;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
@SuppressWarnings("unchecked")
public abstract class AbstractMethodHandler
  implements Comparable<AbstractMethodHandler> {

  protected final AbstractApp app;

  /**
   * The HTTP method (GET, POST, ...)
   */
  private final String httpMethod;

  /**
   * The method to invoke to process requests.
   */
  private final Method javaMethod;

  /**
   * The object to invoke method on (i.e. the actual handler).
   */
  private final Object target;

  private OutputFormatter outputFormat;

  /**
   * The route as defined with the Route annotation. It is an absolute route
   * for the enclosing app so it may not match the original request path if
   * several apps share a given web server.
   */
  private final String route;

  private ArgExtractor[] extractors;

  private final List<BeforeHook> beforeHooks = new Vector<>();

  protected InputParser inputParser;

  public AbstractMethodHandler(AbstractApp app,
                               String route,
                               Method m,
                               Object target) {
    this.app = app;
    this.route = route;
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

  protected abstract ArgExtractor[] createExtractors(Method m);

  /**
   * @param type the type of argument in method
   * @param i the extractor's index (i.e. index of argument in method)
   * @param idx indexes of variables in split URI, eg. { 1 } to extract "world"
   * from
   */
  protected abstract ArgExtractor createExtractor(Method m,
                                                  Class<?> type,
                                                  int i,
                                                  AtomicInteger urlParam,
                                                  int[] idx);

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

  public void setInputParser(InputParser inputParser) {
    this.inputParser = inputParser;
  }

  public static boolean isNotBasic(Class<?> type) {
    return type != String.class && type != byte[].class && type != InputStream.class && type != Response.class && type != void.class;
  }

  private InputParser initInputParser() {
    InputFormat input = javaMethod.getAnnotation(InputFormat.class);
    if (input != null)
      return app.getInputParser(input.value());
    return null;
  }

  private static String getHttpMethod(Method m) {
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
    return "GET";
  }

  /**
   * @return true when request was handled, false when it was ignored (eg. not
   * applicable)
   */
  @SuppressWarnings("unchecked")
  public boolean handle(SPRequest req) throws Exception {

    if (!isApplicable(req))
      return false;

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

    processResponse(req.getResponse(), res);
    return true;
  }

  protected abstract boolean isApplicable(SPRequest req);

  @SuppressWarnings("StatementWithEmptyBody")
  private void processResponse(Response r, Object res) throws Exception {
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
      if (!r.isStatusSet())
        r.setStatus(200);
    }
    else
      throw new RuntimeException("Unexpected return value: " + res + " from " + javaMethod
                                                                                  .toGenericString());

  }

  /**
   * Computes the list of arguments to pass to the decorated method.
   */
  private Object[] extractArgs(SPRequest r) throws Exception {
    Object[] args = new Object[extractors.length];
    for (int i = 0; i < args.length; i++) {
      args[i] = extractors[i].extract(r);
    }
    return args;
  }

  public int compareTo(AbstractMethodHandler o) {
    if (Objects.equals(route, o.route))
      return httpMethod.compareTo(o.httpMethod);
    return route.compareTo(o.route);
  }

  public String getRoute() {
    return route;
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
}
