package flask.test;

import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

public class RouteTest extends AbstractAppTest {

  @Route(value = "/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }

  @Route("/hello/:name/:surname")
  public String hello2(String name, String surname) {
    return "Hello " + name + " " + surname;
  }

  @Route("/db/hello/:name/stuff")
  public String hello3(String name) {
    return "Hello " + name;
  }

  @Test
  public void testHelloWorld() throws Exception {
    Assert.assertEquals("Hello world", client.get("/hello/world"));
    Assert.assertEquals("Hello world 2", client.get("/hello/world/2"));
    Assert.assertEquals("Hello world", client.get("/db/hello/world/stuff"));
  }

  @Test
  public void testSearch() throws Exception {
    Assert.assertEquals("Hello world", client.get("/hello/world?foo=bar"));
  }
}
