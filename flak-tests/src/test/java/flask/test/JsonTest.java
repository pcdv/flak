package flask.test;

import java.io.IOException;

import flak.annotations.JSON;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.jackson.JacksonPlugin;
import org.junit.Assert;
import org.junit.Test;

public class JsonTest extends AbstractAppTest {

  @Override
  protected void preScan() {
    JacksonPlugin.install(app);
  }

  /**
   * Parses a JSON foo object and returns it.
   */
  @Put
  @Route("/api/foo")
  @JSON
  public Object putFoo(OutputFormatTest.Foo foo) {
    return foo;
  }

  /**
   * Check that JSON is correctly decoded and encoded back by route handler.
   */
  @Test
  public void testJsonBackAndForth() throws IOException {
    Assert.assertEquals("{\"stuff\":1235}",
                        client.put("/api/foo", "{\"stuff\":1235}"));
  }

  @Test
  public void errorWhenMissingInputFormat() {
    TestUtil.assertFails(() -> app.scan(new Object() {
      @Route("/foo1")
      public String foo(OutputFormatTest.Foo foo) { return null;}
    }), "No @InputFormat or @JSON found around method foo()");
  }

  @Test
  public void errorWhenMissingOutputFormat() {
    TestUtil.assertFails(() -> {
      app.scan(new Object() {
        @Route("/foo1")
        public OutputFormatTest.Foo foo() { return null;}
      });
      return client.get("/foo1");
    }, "No @OutputFormat or @JSON around method foo()");
  }
}
