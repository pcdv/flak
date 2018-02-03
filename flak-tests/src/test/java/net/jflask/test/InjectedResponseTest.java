package net.jflask.test;

import java.io.IOException;

import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class InjectedResponseTest extends AbstractAppTest {

  @Route("/app")
  public void app(Response response) throws IOException {
    response.setStatus(200);
    response.getOutputStream().write("Hello world".getBytes());
  }

  @Test
  public void voidMethodWithInjectedResponse() throws IOException {
    Assert.assertEquals("Hello world", client.get("/app"));
  }
}
