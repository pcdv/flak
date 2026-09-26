package flak.backend.netty;

import flak.spi.AbstractMethodHandler;
import flak.spi.HandlerSpec;
import flak.spi.util.Log;

import java.util.List;

public class NettyMethodHandler extends AbstractMethodHandler {
  final NettyRoute route;

  /**
   * @param app    the enclosing App
   * @param route  the parent route, e.g. if path is /foo/bar/:id, the route corresponds
   *               to "bar", itself a child of "foo"
   * @param tokens result of splitting the path and chopping all constant elements,
   *               e.g. if path is /foo/bar/:id/stuff then tokens = [ ":id", "stuff" ]
   * @param spec   the method to invoke, and how to call it
   * @param obj    the object on which the method is invoked
   */
  public NettyMethodHandler(NettyApp app, NettyRoute route, List<String> tokens, HandlerSpec spec, Object obj) {
    super(app,
          // no trailing slash when the route has no dynamic token, so that
          // getRoute() reads the same as with the other backends
          tokens.isEmpty() ? route.path : route.path + "/" + String.join("/", tokens),
          tokens.toArray(new String[0]),
          spec,
          obj);
    this.route = route;

    Log.debug("Create handler " + this);
  }

  /**
   * @return true if this handler matched the request and ran
   */
  public boolean handle(NettyRequest req) throws Exception {
    if (!isApplicable(req))
      return false;

    processResponse(req, execute(req));
    return true;
  }

  @Override
  public String toString() {
    return route + "[" + path + "] " + javaMethod.toGenericString();
  }
}
