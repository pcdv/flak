package flask.test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import flak.App;
import flak.AppFactory;
import flak.annotations.Route;
import flak.util.RouteDumper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a route of an app hosted at a path looks like from outside. The two
 * backends used to disagree: the JDK one reported the path of the app as part
 * of the route, the netty one did not.
 */
public class AppPathRouteTest {

  private App app;

  public static class Handlers {
    @Route("/api/foo/:id")
    public String foo(String id) {
      return id;
    }
  }

  @Before
  public void setUp() throws Exception {
    AppFactory fac = TestUtil.getFactory();
    fac.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    app = fac.createApp("/myapp");
    app.scan(new Handlers());
  }

  @After
  public void tearDown() {
    if (app != null)
      app.getServer().stop();
  }

  @Test
  public void routeIsRelativeToTheApp() {
    assertEquals("/myapp", app.getPath());
    assertEquals("/api/foo/:id", app.getHandlers().findFirst().get().getRoute());
  }

  /**
   * A handler is therefore looked up with the route as it was declared, not
   * with the path of the app prepended.
   */
  @Test
  public void handlerIsFoundByTheRouteAsDeclared() {
    assertEquals("foo", app.getHandler("GET", "/api/foo/:id").getJavaMethod().getName());
  }

  @Test
  public void dumpedRoutesAreAbsoluteAndNotDoubled() {
    String dump = new RouteDumper().dumpRoutes(app, new StringBuilder()).toString();

    assertTrue(dump, dump.contains("/myapp/api/foo/:id"));
    assertFalse(dump, dump.contains("/myapp/myapp"));
  }
}
