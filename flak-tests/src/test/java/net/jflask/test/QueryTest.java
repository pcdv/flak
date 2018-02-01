package net.jflask.test;

import java.io.IOException;

import flak.Request;
import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QueryTest extends AbstractAppTest {

  @Route("/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }

  @Test
  public void testTrimQS() throws Exception {
    assertEquals("Hello world", client.get("/hello/world?foo=bar"));
  }

  @Route("/hello2")
  public String helloQuery() {
    return "Hello " + app.getRequest().getQuery().get("name", null);
  }

  @Test
  public void testGetArgSlash() throws Exception {
    assertEquals("Hello world", client.get("/hello2/?name=world"));
  }

  @Test
  public void testGetArg() throws Exception {
    assertEquals("Hello world", client.get("/hello2?name=world"));
  }

  @Route("/hello_bytearray")
  public byte[] helloByteArray(Request req) {
    return ("Hello " + req.getQuery().get("name", null)).getBytes();
  }

  @Test
  public void testReturnByteArray() throws Exception {
    assertEquals("Hello world", client.get("/hello_bytearray?name=world"));
  }

  @Route("/hello/request")
  public String getApp(Request req) {
    return "Hello " + req.getQuery().get("name", "???");
  }

  @Test
  public void testRequestInjectedInMethodArgs() throws IOException {
    assertEquals("Hello world", client.get("/hello/request?name=world"));
    assertEquals("Hello ???", client.get("/hello/request?foo=bar"));
    assertEquals("Hello ???", client.get("/hello/request?"));
    assertEquals("Hello ???", client.get("/hello/request"));
  }
}
