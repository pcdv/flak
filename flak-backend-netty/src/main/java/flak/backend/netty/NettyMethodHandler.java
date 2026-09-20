package flak.backend.netty;

import flak.spi.AbstractMethodHandler;
import flak.spi.BeforeHook;
import flak.spi.util.Log;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.lang.reflect.Method;
import java.util.List;

public class NettyMethodHandler extends AbstractMethodHandler {
  final NettyRoute route;

  /**
   * @param app    the enclosing App
   * @param route  the parent route, e.g. if path is /foo/bar/:id, the route corresponds
   *               to "bar", itself a child of "foo"
   * @param tokens result of splitting the path and chopping all constant elements,
   *               e.g. if path is /foo/bar/:id/stuff then tokens = [ ":id", "stuff" ]
   * @param method the method to invoke
   * @param obj    the object on which the method is invoked
   */
  public NettyMethodHandler(NettyApp app, NettyRoute route, List<String> tokens, Method method, Object obj) {
    super(app,
          route.path + "/" + String.join("/", tokens),
          tokens.toArray(new String[0]),
          method,
          obj);
    this.route = route;

    Log.info("Create handler " + this);
  }

  public HttpResponse getResponse(ChannelHandlerContext ctx, FullHttpRequest r) throws Exception {
    NettyRequest req = new NettyRequest(this, ctx, r);
    app.setThreadLocalRequest(req);

    if (!isApplicable(req))
      return null;

    try {
      processResponse(req.getResponse(), execute(req));
    }
    catch (BeforeHook.StopProcessingException e) {
      Log.debug("Stop processing");
    }

    return req.getResponse().toHttpResponse();
  }

  @Override
  public String toString() {
    return route + "[" + path + "] " + javaMethod.toGenericString();
  }
}
