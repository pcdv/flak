package flak.backend.jdk;

import flak.spi.AbstractMethodHandler;

import java.lang.reflect.Method;

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
}
