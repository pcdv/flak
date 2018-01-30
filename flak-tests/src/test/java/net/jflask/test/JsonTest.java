package net.jflask.test;

import java.io.IOException;

import flak.annotations.JSON;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.jackson.JsonInputParser;
import flak.jackson.JsonOutputFormatter;
import net.jflask.test.OutputFormatTest.Foo;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class JsonTest extends AbstractAppTest {

  @Override
  protected void preScan() {
    app.addInputParser("JSON", new JsonInputParser());
    app.addOutputFormatter("JSON", new JsonOutputFormatter<>());
  }

  /**
   * Parses a JSON foo object and returns it.
   */
  @Put
  @Route("/api/foo")
  @JSON
  public Object putFoo(Foo foo) {
    return foo;
  }

  @Test
  public void testJsonBackAndForth() throws IOException {
    Assert.assertEquals("{\"stuff\":1235}",
                        client.put("/api/foo", "{\"stuff\":1235}"));
  }

  @Test
  public void checkErrors() {
    try {
      app.scan(new Object() {
        @Route("/foo1")
        public String foo(Foo foo) { return null;}
      });
      Assert.fail("Should have failed");
    }
    catch (Exception e) {
      Assert.assertEquals("No @InputFormat or @JSON found in method foo",
                          e.getMessage());
    }
  }
}
