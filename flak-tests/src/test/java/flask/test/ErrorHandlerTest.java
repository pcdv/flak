package flask.test;

import flak.ErrorHandler;
import flak.HttpException;
import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Check that an {@link ErrorHandler} can intercept an exception in a
 * route handler and reply anything to the client.
 *
 * @author pcdv
 */
public class ErrorHandlerTest extends AbstractAppTest {

  @Route("/")
  public String foo() {
    throw new IllegalStateException("fail");
  }

  /**
   * Check that a error handler can customize the response sent to the client.
   */
  @Test
  public void testHandlerSendsContent() throws Exception {
    addDummyErrorHandler();

    try {
      client.get("/");
    }
    catch (HttpException e) {
      Assert.assertEquals(555, e.getResponseCode());
      Assert.assertEquals("hello", e.getMessage());
    }
  }

  /**
   * Reproduce conflict between error handler and unknown path (404) management.
   * Problem was due to: error handler called in case of 404, exception thrown
   * later in low level flak code, error handler called again due to exception.
   * Solution is to no longer invoke error handler on 4040 (there is already
   * unknown page handler) and to avoid the exception when HTTP headers are
   * already flushed.
   */
  @Test
  public void test404() throws Exception {
    // this should no longer have any effect when 404 happens
    addDummyErrorHandler();

    app.setUnknownPageHandler(request -> {
      Response r = request.getResponse();
      try {
        r.setStatus(502);
        r.addHeader("Content-Type", "text/html");
        r.getOutputStream().write("unknown".getBytes());
        r.getOutputStream().close(); // this will flush the headers
      }
      catch (IOException ignored) {
      }
    });

    try {
      client.get("/invalid");
    }
    catch (HttpException e) {
      Assert.assertEquals(502, e.getResponseCode());
      Assert.assertEquals("unknown", e.getMessage());
    }
  }

  private void addDummyErrorHandler() {
    app.addErrorHandler((status, request, t) -> {
      // this is a hack (future version should allow to do it in a clean way)
      Response r = request.getResponse();
      try {
        r.setStatus(555);
        r.addHeader("Content-Type", "text/html");
        r.getOutputStream().write("hello".getBytes());
        r.getOutputStream().close();
      }
      catch (IOException ignored) {
      }
    });
  }
}
