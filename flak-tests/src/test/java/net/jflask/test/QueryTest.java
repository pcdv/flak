package net.jflask.test;

import flak.annotations.Route;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QueryTest extends AbstractAppTest {

  @Route("/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }

  @Route("/hello2")
  public String helloQuery() {
    return "Hello " + app.getRequest().getArg("name", null);
  }

  @Route("/hello_bytearray")
  public byte[] helloByteArray() {
    return ("Hello " + app.getRequest().getArg("name", null)).getBytes();
  }

  @Test
  public void testTrimQS() throws Exception {
    assertEquals("Hello world", client.get("/hello/world?foo=bar"));
  }

  @Test
  public void testGetArg() throws Exception {
    assertEquals("Hello world", client.get("/hello2?name=world"));
  }

  @Test
  @Ignore // looks like URL does not work with trailing slash
  public void testGetArgSlash() throws Exception {
    assertEquals("Hello world", client.get("/hello2/?name=world"));
  }

  @Test
  public void testReturnByteArray() throws Exception {
    assertEquals("Hello world", client.get("/hello_bytearray?name=world"));
  }

}
