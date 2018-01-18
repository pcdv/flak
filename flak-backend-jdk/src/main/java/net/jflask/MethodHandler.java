package net.jflask;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.sun.net.httpserver.HttpExchange;
import flak.Response;
import flak.ResponseConverter;
import flak.annotations.Convert;
import flak.annotations.Delete;
import flak.annotations.LoginNotRequired;
import flak.annotations.LoginPage;
import flak.annotations.LoginRequired;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.util.IO;
import flak.util.Log;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
public class MethodHandler implements Comparable<MethodHandler> {

  private static final String[] EMPTY = {};

  /**
   * The HTTP method
   */
  private final String requestMethod;

  /**
   * The method to invoke to process requests.
   */
  private final Method method;

  /**
   * The object to invoke method on.
   */
  private final Object target;

  /**
   * The split URI, eg. { "hello", ":name" }
   */
  private final String[] tok;

  /**
   * The indexes of variables in split URI, eg. { 1 } to extract "world" from
   * "/hello/world" if URI schema is "/hello/:name"
   */
  private final int[] idx;

  private final String converterName;

  private boolean loginRequired;

  private int splat = -1;

  private final String rootURI;

  private final Context ctx;

  @SuppressWarnings("rawtypes")
  private ResponseConverter converter;

  private final String uri;

  public MethodHandler(Context ctx, String uri, Method method, Object target) {
    this.ctx = ctx;
    this.uri = uri;
    this.rootURI = uri;
    this.requestMethod = initHttpRequest(method);
    this.converterName = initConverterName(method);
    this.method = method;
    this.target = target;
    this.tok = uri.isEmpty() ? EMPTY : uri.substring(1).split("/");
    this.idx = calcIndexes(tok);

    if (method.getAnnotation(LoginPage.class) != null)
      ctx.app.setLoginPage(ctx.getRootURI() + uri);

    configure();

    // hack for being able to call method even if not public or if the class
    // is not public
    if (!method.isAccessible())
      method.setAccessible(true);

    for (Class<?> c : method.getParameterTypes()) {
      if (c != String.class)
        throw new RuntimeException//
                  ("Only String supported in method arguments (for now): " +
                   method);
    }
  }

  private static String initConverterName(Method m) {
    Convert c = m.getAnnotation(Convert.class);
    if (c != null)
      return c.value();

    Route route = m.getAnnotation(Route.class);
    if (route.converter().isEmpty())
      return null;
    return route.converter();
  }

  private static String initHttpRequest(Method m) {
    if (m.getAnnotation(Post.class) != null)
      return "POST";
    if (m.getAnnotation(Put.class) != null)
      return "PUT";
    if (m.getAnnotation(Patch.class) != null)
      return "PATCH";
    if (m.getAnnotation(Delete.class) != null)
      return "DELETE";
    Route route = m.getAnnotation(Route.class);
    return route.method();
  }

  /**
   * Called during construction and when App configuration changes that may
   * require adaptation in handlers.
   */
  public void configure() {
    if (method.getAnnotation(LoginRequired.class) != null)
      loginRequired = true;
    else if (method.getAnnotation(LoginNotRequired.class) != null ||
             method.getAnnotation(LoginPage.class) != null)
      loginRequired = false;
    else
      loginRequired = ctx.app.getRequireLoggedInByDefault();

    if (this.converter == null && converterName != null)
      this.converter = ctx.app.getConverter(converterName);
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
  public boolean handle(HttpExchange r,
                        String[] uri,
                        Response resp) throws Exception {

    if (!isApplicable(r, uri))
      return false;

    if (loginRequired && !ctx.app.checkLoggedIn(r)) {
      return true;
    }

    Object[] args = extractArgs(uri);

    if (Log.DEBUG)
      Log.debug("Invoking " + target.getClass().getSimpleName() + "." +
                method.getName() +
                Arrays.toString(args));

    Object res = method.invoke(target, args);

    ctx.app.fireSuccess(method, args, res);

    return processResponse(r, resp, res);
  }

  private boolean processResponse(HttpExchange r,
                                  Response resp,
                                  Object res) throws Exception {
    if (converter != null) {
      converter.convert(res, resp);
    }
    else if (converterName != null) {
      throw new IllegalStateException("Converter '" + converterName +
                                      "' not registered in App.");
    }
    else if (res instanceof Response) {
      // do nothing: status and headers should already be set
    }
    else if (res instanceof String) {
      r.sendResponseHeaders(200, 0);
      r.getResponseBody().write(((String) res).getBytes("UTF-8"));
    }
    else if (res instanceof byte[]) {
      r.sendResponseHeaders(200, 0);
      r.getResponseBody().write((byte[]) res);
    }
    else if (res instanceof InputStream) {
      r.sendResponseHeaders(200, 0);
      IO.pipe((InputStream) res, r.getResponseBody(), false);
    }
    else
      throw new RuntimeException("Unexpected return value: " + res + " from " +
                                 method.toGenericString());

    return true;
  }

  /**
   * Computes the list of arguments to pass to the decorated method.
   */
  private String[] extractArgs(String[] uri) {
    String[] args = new String[idx.length];
    for (int i = 0; i < args.length; i++) {
      args[i] = uri[idx[i]];
    }
    if (splat != -1) {
      for (int i = splat + 1; i < uri.length; i++) {
        args[args.length - 1] += "/" + uri[i];
      }
    }
    return args;
  }

  /**
   * Checks whether current handler should respond to specified request.
   */
  private boolean isApplicable(HttpExchange r, String[] uri) {
    if (!r.getRequestMethod().equals(this.requestMethod))
      return false;

    if (uri.length != tok.length) {
      if (splat == -1 || uri.length < tok.length)
        return false;
    }

    for (int i = 0; i < tok.length; i++) {
      if (tok[i].charAt(0) != ':' && tok[i].charAt(0) != '*' &&
          !tok[i].equals(uri[i]))
        return false;
    }

    return true;
  }

  public int compareTo(MethodHandler o) {
    if (Arrays.equals(tok, o.tok))
      return requestMethod.compareTo(o.requestMethod);
    return uri.compareTo(o.uri);
  }

  public String getURI() {
    return uri;
  }

  public String getVerb() {
    return requestMethod;
  }

  public Method getMethod() {
    return method;
  }

}
