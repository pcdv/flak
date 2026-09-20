package flak.util;

import flak.App;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author pcdv
 */
public class RouteDumper {

  /**
   * Dumps all registered URLs/methods in a readable way into specified buffer.
   * This can be useful to generate reports or to document an API.
   */
  public StringBuilder dumpRoutes(App app, StringBuilder b) {

    Map<String, List<AbstractMethodHandler>> byPrefix = new TreeMap<>();

    ((AbstractApp) app).getMethodHandlers()
                       .forEach(h -> byPrefix.computeIfAbsent(staticPrefix(h.getRoute()),
                                                              k -> new ArrayList<>())
                                             .add(h));

    for (Map.Entry<String, List<AbstractMethodHandler>> e : byPrefix.entrySet()) {
      b.append(app.getPath()).append(e.getKey()).append(":\n");

      List<AbstractMethodHandler> handlers = e.getValue();
      Collections.sort(handlers);

      for (AbstractMethodHandler mh : handlers) {
        b.append(String.format("%-50s  %-8s  %-15s %s\n",
                               app.getPath() + mh.getRoute(),
                               mh.getHttpMethod(),
                               mh.getJavaMethod().getName(),
                               mh.getJavaMethod().getDeclaringClass().getName()));
      }

      b.append('\n');
    }

    return b;
  }

  /**
   * Returns the constant part of a route, i.e. what precedes its first
   * variable or splat token: routes are grouped under it.
   */
  private static String staticPrefix(String route) {
    StringBuilder res = new StringBuilder(route.length());

    for (String token : route.split("/")) {
      if (token.isEmpty())
        continue;
      if (token.charAt(0) == ':' || token.charAt(0) == '*')
        break;
      res.append('/').append(token);
    }

    return res.toString();
  }
}
