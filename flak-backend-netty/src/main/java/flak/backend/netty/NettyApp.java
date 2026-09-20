package flak.backend.netty;

import flak.WebServer;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.stream.Stream;

import static flak.spi.AbstractMethodHandler.getHttpMethod;

public class NettyApp extends AbstractApp {
  private final NettyWebServer srv;
  private boolean started;
  private final HashMap<String, NettyRoute> routeByMethod = new HashMap<>();

  public NettyApp(String rootUrl, NettyWebServer srv) {
    super(rootUrl);
    this.srv = srv;
  }

  @Override
  public void start() throws IOException {
    if (started)
      throw new IllegalStateException("Already started");
    started = true;
    srv.addApp(this);

    if (!srv.isStarted())
      srv.start();
  }

  @Override
  public void stop() {
    srv.removeApp(this);
  }

  @Override
  public WebServer getServer() {
    return srv;
  }

  @Override
  protected AbstractMethodHandler addHandler(String route, Method method, Object target) {
    NettyRoute nr = routeByMethod.computeIfAbsent
      (getHttpMethod(method),
       m -> new NettyRoute(this, null, "", m));
    return nr.addHandler(route, method, target);
  }

  public boolean route(NettyRequest req, String[] tokens, int i) throws Exception {
    NettyRoute route = routeByMethod.get(req.getMethod());
    return route != null && route.dispatch(req, tokens, i);
  }

  @Override
  public Stream<AbstractMethodHandler> getMethodHandlers() {
    return routeByMethod.values().stream().flatMap(r -> r.getMethodHandlers());
  }
}
