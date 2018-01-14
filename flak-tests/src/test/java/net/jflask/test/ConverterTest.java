package net.jflask.test;

import java.io.IOException;

import flak.annotations.Convert;
import flak.annotations.Route;
import flak.Response;
import flak.ResponseConverter;
import flak.jackson.JsonOutputConverter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConverterTest extends AbstractAppTest {

  @Convert("STAR")
  @Route("/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }

  @Override
  protected void preScan() {
    app.addConverter("FOO", new ResponseConverter<String>() {
      public void convert(String data, Response resp) throws Exception {
        resp.setStatus(200);
        resp.getOutputStream().write(("FOO " + data).getBytes());
      }
    });
  }

  @Route(value = "/hello2/:name", converter = "FOO")
  public String hello2(String name) {
    return "Hello " + name;
  }

  @Test
  public void testConverterAddedAfterStart() throws Exception {
    app.addConverter("STAR", new ResponseConverter<String>() {
      public void convert(String data, Response resp) throws Exception {
        resp.setStatus(200);
        resp.getOutputStream().write(("*" + data + "*").getBytes());
      }
    });
    assertEquals("*Hello world*", client.get("/hello/world?foo=bar"));
  }

  @Test
  public void testConverterAddedBeforeScan() throws Exception {
    assertEquals("FOO Hello world", client.get("/hello2/world"));
  }

  public static class Foo {
    public int stuff = 42;
  }

  @Route("/json/getFoo")
  @Convert("JSON")
  public Foo getFoo() {
    return new Foo();
  }

  @Test
  public void testJSON() throws IOException {
    app.addConverter("JSON", new JsonOutputConverter<>());

    String s = client.get("/json/getFoo");
    assertEquals("{\"stuff\":42}", s);
  }
}
