package flask.test;

import java.io.IOException;

import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class VoidMethodTests extends AbstractAppTest {

  @Route("/app")
  public void app(Response response) throws IOException {
    response.setStatus(200);
    response.getOutputStream().write("Hello world".getBytes());
  }

  @Route("/empty")
  public void empty() {
  }

  @Route("/redirect")
  public void redirect() {
    app.redirect("/app");
  }

  @Test
  public void voidMethodWithInjectedResponse() throws IOException {
    Assert.assertEquals("Hello world", client.get("/app"));
  }

  @Test
  public void voidMethodThatDoesNothingReturns() throws IOException {
    Assert.assertEquals("", client.get("/empty"));
  }

  @Test
  public void voidMethodThatRedirects() throws IOException {
    Assert.assertEquals("Hello world", client.get("/redirect"));
  }

}
