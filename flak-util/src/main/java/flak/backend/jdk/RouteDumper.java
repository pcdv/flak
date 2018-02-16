package flak.backend.jdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author pcdv
 */
public class RouteDumper {

  /**
   * Dumps all registered URLs/methods in a readable way into specified buffer.
   * This can be useful to generate reports or to document an API.
   */
  public StringBuilder dumpRoutes(JdkApp app, StringBuilder b) {

    ArrayList<Context> contexts = new ArrayList<>();
    for (Context h : app.getHandlers()) {
      contexts.add(h);
    }

    Collections.sort(contexts, new Comparator<Context>() {
      public int compare(Context o1, Context o2) {
        return o1.getRootURI().compareTo(o2.getRootURI());
      }
    });

    for (Context c : contexts) {
      RouteDumper.dumpUrls(c, b);
      b.append('\n');
    }

    return b;
  }

  public static void dumpUrls(Context c, StringBuilder b) {
    b.append(c.rootURI).append(":\n");

    ArrayList<MethodHandler> list = new ArrayList<>(c.handlers);
    Collections.sort(list);

    for (MethodHandler mh : list) {
      b.append(String.format("%-50s  %-8s  %-15s %s\n",
                             c.rootURI + mh.getRoute(),
                             mh.getHttpMethod(),
                             mh.getJavaMethod().getName(),
                             mh.getJavaMethod().getDeclaringClass().getName()));
    }
  }
}
