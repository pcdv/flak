package flask.test;

import java.io.IOException;

import flak.annotations.OutputFormat;
import flak.annotations.Route;
import flak.jackson.JsonOutputFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OutputFormatTest extends AbstractAppTest {

  @Override
  protected void preScan() {
    app.addOutputFormatter("JSON", new JsonOutputFormatter<>());

    app.addOutputFormatter("FOO", (data, resp) -> {
      resp.setStatus(200);
      resp.getOutputStream().write(("FOO " + data).getBytes());
    });
  }

  @Route(value = "/hello2/:name")
  @OutputFormat("FOO")
  public String hello2(String name) {
    return "Hello " + name;
  }

  @Test
  public void testConverterAddedBeforeScan() throws Exception {
    assertEquals("FOO Hello world", client.get("/hello2/world"));
  }

  public static class Foo {
    @SuppressWarnings("unused")
    public int stuff = 42;
  }

  @Route("/json/getFoo")
  @OutputFormat("JSON")
  public Foo getFoo() {
    return new Foo();
  }

  @Test
  public void testJSON() throws IOException {
    String s = client.get("/json/getFoo");
    assertEquals("{\"stuff\":42}", s);
  }
}
