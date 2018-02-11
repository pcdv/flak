package flak.backend.jdk;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import flak.HttpException;
import flak.RequestHandler;
import flak.util.Log;

/**
 * A HTTP handler that receives all requests on a given rootURI and dispatches
 * them to configured route handlers.
 *
 * @author pcdv
 */
public class Context implements HttpHandler, RequestHandler {

  final String rootURI;

  final List<MethodHandler> handlers = new ArrayList<>();

  final JdkApp app;

  /**
   * Helps converting an absolute path to a relative path.
   */
  private final int rootURIOffset;

  Context(JdkApp app, String rootURI) {
    this.app = app;
    this.rootURI = rootURI;
    this.rootURIOffset =
      rootURI.endsWith("/") ? rootURI.length() - 1 : rootURI.length();
  }

  /**
   * Registers a java method that must be called to process requests matching
   * specified URI (relative to rootURI).
   *
   * @param uri URI schema relative to rootURI (eg. "/:name")
   * @param method a java method
   * @param obj the object on which the method must be invoked
   */
  public MethodHandler addHandler(String uri, Method method, Object obj) {
    MethodHandler handler = new MethodHandler(this, uri, method, obj);
    handlers.add(handler);
    return handler;
  }

  public String getRootURI() {
    return rootURI;
  }

  public void handle(HttpExchange r) throws IOException {
    String path = r.getRequestURI().getPath();
    JdkRequest req =
      new JdkRequest(app.makeRelativePath(path), makeRelativePath(path), r);
    app.setThreadLocalRequest(req);
    try {
      for (MethodHandler h : handlers) {
        if (h.handle(req)) {
          return;
        }
      }

      app.on404(req);
    }
    catch (Throwable t) {

      if (t instanceof InvocationTargetException) {
        t = ((InvocationTargetException) t).getTargetException();

        if (t instanceof HttpException) {
          r.sendResponseHeaders(((HttpException) t).getResponseCode(), 0);
          r.getResponseBody().write(t.getMessage().getBytes("UTF-8"));
          return;
        }
      }

      app.fireError(500, req, t);
      Log.error(t, t);
      r.sendResponseHeaders(500, 0);
      if (app.isDebugEnabled()) {
        t.printStackTrace(new PrintStream(r.getResponseBody()));
      }
    } finally {
      r.getResponseBody().close();
    }
  }

  /**
   * Returns the URI relative to the current app. E.g. if app root is "/app1"
   * and URI is "/app1/foo", this will return "/foo".
   */
  private String makeRelativePath(String uri) {
    return uri.substring(rootURIOffset);
  }

  @Override
  public JdkApp getApp() {
    return app;
  }
}
