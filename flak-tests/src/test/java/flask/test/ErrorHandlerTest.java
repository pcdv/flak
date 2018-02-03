package flask.test;

import java.io.IOException;

import flak.ErrorHandler;
import flak.HttpException;
import flak.Request;
import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

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
    app.addErrorHandler(new ErrorHandler() {
      @Override
      public void onError(int status, Request request, Throwable t) {
        // this is a hack (future version should allow to do it in a clean way)
        Response r = app.getResponse();
        try {
          r.setStatus(555);
          r.getOutputStream().write("hello".getBytes());
        }
        catch (IOException ignored) {
        }
      }
    });

    try {
      client.get("/");
    }
    catch (HttpException e) {
      Assert.assertEquals(555, e.getResponseCode());
      Assert.assertEquals("hello", e.getMessage());
    }
  }
}
