package flak.backend.netty;

import flak.spi.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class NettyRoute {
  private final NettyApp app;
  final String path;
  final NettyRoute parent;
  private final String httpMethod;
  private final ArrayList<NettyMethodHandler> self = new ArrayList<>();
  private final HashMap<String, NettyRoute> routes = new HashMap<>();
  final int level;

  public NettyRoute(NettyApp app, NettyRoute parent, String path, String httpMethod) {
    this.parent = parent;
    this.level = parent == null ? 0 : parent.level + 1;
    this.app = app;
    this.path = path;
    this.httpMethod = httpMethod;
    Log.info("Create route " + httpMethod + " " + path);
    assert path.isEmpty() || !path.matches("[:*].*");
  }

  public NettyMethodHandler addHandler(String route, Method method, Object target) {
    List<String> tokens = route.equals("/")
      ? Collections.emptyList()
      : Arrays.asList(route.replaceAll("^/*(.*?)/*$", "$1").split("/+"));
    return addHandler(tokens, method, target);
  }

  public NettyMethodHandler addHandler(List<String> tokens, Method method, Object target) {
    if (tokens.isEmpty() || tokens.get(0).matches("[:*].*")) {
      NettyMethodHandler handler = new NettyMethodHandler(app,
                                                          this,
                                                          tokens,
                                                          method,
                                                          target);
      self.add(handler);
      return handler;
    }

    return routes.computeIfAbsent(tokens.get(0),
                                  h -> new NettyRoute(app,
                                                      this,
                                                      path + "/" + h,
                                                      httpMethod))
                 .addHandler(tokens.subList(1, tokens.size()), method, target);
  }

  @Override
  public String toString() {
    return httpMethod + " " + path;
  }

  public boolean dispatch(NettyRequest req, String[] tokens, int i) throws Exception {
    if (i < tokens.length) {
      NettyRoute route = routes.get(tokens[i]);
      if (route != null && route.dispatch(req, tokens, i + 1))
        return true;
    }

    // handlers of this route only see the tokens that follow it
    req.setRouteLevel(level);

    for (NettyMethodHandler h : self) {
      if (h.handle(req))
        return true;
    }

    return false;
  }

  public Stream<NettyMethodHandler> getMethodHandlers() {
    return Stream.concat(routes.values().stream().flatMap(NettyRoute::getMethodHandlers),
                         self.stream());
  }
}
