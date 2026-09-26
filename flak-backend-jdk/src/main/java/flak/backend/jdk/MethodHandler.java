package flak.backend.jdk;

import flak.spi.AbstractMethodHandler;
import flak.spi.HandlerSpec;

/**
 * Handles a request submitted by the Context, if compatible with the HTTP
 * method and URI schema.
 *
 * @author pcdv
 */
public class MethodHandler extends AbstractMethodHandler {

  private static final String[] EMPTY = {};

  MethodHandler(Context ctx, String uri, HandlerSpec spec, Object target) {
    super(ctx.app,
          ctx.getRootURI() + uri,
          uri.isEmpty() ? EMPTY : uri.substring(1).split("/"),
          spec,
          target);
  }

  /**
   * The path we are built with is absolute, the context being created with an
   * absolute root, but a route is relative to the app that declares it: that
   * is what was written in @Route, and what the netty backend reports.
   */
  @Override
  public String getRoute() {
    String route = super.getRoute();
    String appPath = app.getPath();
    return !appPath.isEmpty() && route.startsWith(appPath)
      ? route.substring(appPath.length())
      : route;
  }
}
