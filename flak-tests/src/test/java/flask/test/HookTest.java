package flask.test;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import flak.HttpException;
import flak.annotations.Route;
import flak.spi.AbstractApp;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("CodeBlock2Expr")
public class HookTest extends AbstractAppTest {

  /**
   * This one conflicts with initial declaration for ErrorHandler.
   */
  @Route("/")
  public String root() {
    return "root";
  }

  @Route("/barf")
  public String barf() {
    throw new RuntimeException("barf");
  }

  @Route("/hello/:name")
  public String getOk(String name) {
    return "Hello " + name;
  }

  /**
   * Check that 404 and other errors can be handled by ErrorHandlers.
   */
  @Test
  public void testErrorHook() throws Exception {

    final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

    app.addErrorHandler((status, request, t) -> {
      queue.offer(status + " " + request.getMethod() + " " + request.getPath() + " " + t);
    });

    try {
      client.get("/unknown");
    }
    catch (HttpException ignored) {
    }

    Assert.assertEquals(null,
                        queue.poll(10, TimeUnit.MILLISECONDS));

    try {
      client.get("/barf");
    }
    catch (HttpException ignored) {
    }

    Assert.assertEquals("500 GET /barf java.lang.RuntimeException: barf",
                        queue.poll(1, TimeUnit.SECONDS));

    Assert.assertEquals("root", client.get("/"));
    Assert.assertNull(queue.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testSuccessHook() throws Exception {
    final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

    app.addSuccessHandler((r, method, args, result) -> {
      queue.offer(r.getMethod() + " " + r.getPath() + " " + method.getName() + Arrays
                                                                                 .toString(
                                                                                   args) + " " + result);
    });

    client.get("/hello/world");

    Assert.assertEquals("GET /hello/world getOk[world] Hello world",
                        queue.poll(1, TimeUnit.SECONDS));

    Assert.assertEquals("root", client.get("/"));
    Assert.assertEquals("GET / root[] root", queue.poll(1, TimeUnit.SECONDS));
  }

  @Test
  public void testBeforeAllHook() throws Exception {
    ((AbstractApp) app).addBeforeAllHook(r -> {
      r.getResponse().addHeader("X-forwarded-by", "172.16.0.5");
    });

    Assert.assertEquals("172.16.0.5",
                        client.head("/hello/world") // 200 OK
                              .get("X-forwarded-by")
                              .get(0));

    Assert.assertEquals("172.16.0.5",
                        client.head("/an/unhandled/route") // 404 Not found
                              .get("X-forwarded-by")
                              .get(0));
  }
}
