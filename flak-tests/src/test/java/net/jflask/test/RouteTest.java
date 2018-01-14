package net.jflask.test;

import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RouteTest extends AbstractAppTest {

  @Route(value = "/hello/:name", method = "GET")
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
    assertEquals("Hello world", client.get("/hello/world"));
    assertEquals("Hello world 2", client.get("/hello/world/2"));
    assertEquals("Hello world", client.get("/db/hello/world/stuff"));
  }

  @Test
  public void testSearch() throws Exception {
    assertEquals("Hello world", client.get("/hello/world?foo=bar"));
  }
}
