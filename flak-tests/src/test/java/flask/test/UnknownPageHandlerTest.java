package flask.test;

import java.io.IOException;

import flak.Request;
import flak.Response;
import flak.UnknownPageHandler;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

public class UnknownPageHandlerTest extends AbstractAppTest {

  @Route("/foo")
  public String foo() {
    return "bar";
  }

  @Route("/")
  public String foo2() {
    return "root";
  }

  @Test
  public void testIt() throws Exception {
    app.setUnknownPageHandler(new UnknownPageHandler() {
      @Override
      public void handle(Request r) throws IOException {
        Response resp = app.getResponse();
        resp.setStatus(200);
        resp.getOutputStream().write("gotcha".getBytes());
      }
    });

    Assert.assertEquals("bar", client.get("/foo"));
    Assert.assertEquals("gotcha", client.get("/bar"));
  }
}
