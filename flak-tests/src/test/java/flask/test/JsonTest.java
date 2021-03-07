package flask.test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.backend.jdk.JdkApp;
import flak.jackson.JSON;
import flak.jackson.JsonInputReader;
import flak.spi.AbstractMethodHandler;
import flask.test.OutputFormatTest.Foo;
import org.junit.Assert;
import org.junit.Test;

public class JsonTest extends AbstractAppTest {

  /**
   * Parses a JSON foo object and returns it.
   */
  @Put
  @Route("/api/foo")
  @JSON
  public Foo putFoo(Foo foo) {
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
      public String foo(Foo foo) { return null;}
    }), "No @InputFormat or @JSON found around method foo()");
  }

  @Test
  public void errorWhenMissingOutputFormat() {
    TestUtil.assertFails(() -> {
      app.scan(new Object() {
        @Route("/foo1")
        public Foo foo() { return null;}
      });
      return client.get("/foo1");
    }, "No @OutputFormat or @JSON around method foo()");
  }

  @Route("/api/jsonMap")
  @Post
  @JSON
  public Map<?, ?> postMap(Map<String, Object> map) {
    map.put("status", "ok");
    return map;
  }

  @Route("/api/jsonMapVoid2")
  @Post
  public void postMapVoid2(@JSON Map<String, Object> map) {
    map.put("status", "ok");
  }

  @Route("/api/jsonMapVoid")
  @Post
  @JSON
  public void postMapVoid(Map<String, Object> map) {
    map.put("status", "ok");
  }

  @Test
  public void testJsonMap() throws Exception {
    Map<String, Object> m = new HashMap<>();
    m.put("foo", "bar");
    String reply = client.post("/api/jsonMap", new ObjectMapper().writeValueAsString(m));

    // checks that the parser uses an ObjectReader (more performant than using ObjectMapper)
    Assert.assertTrue(getMethodHandler("postMap").getInputParser() instanceof JsonInputReader);
    Assert.assertTrue(getMethodHandler("postMapVoid").getInputParser() instanceof JsonInputReader);
    Assert.assertTrue(getMethodHandler("postMapVoid2").getInputParser() instanceof JsonInputReader);

    Map<?, ?> r = new ObjectMapper().readValue(reply, Map.class);
    Assert.assertEquals("ok", r.get("status"));
    Assert.assertEquals("bar", r.get("foo"));

    client.post("/api/jsonMapVoid", new ObjectMapper().writeValueAsString(m));
    client.post("/api/jsonMapVoid2", new ObjectMapper().writeValueAsString(m));
  }

  AbstractMethodHandler getMethodHandler(String name) {
    return ((JdkApp) app).getMethodHandlers().filter(h -> h.getJavaMethod().getName().equals(name)).findAny().get();
  }

  @JSON
  @Route("/api/getVersions/:group")
  public Collection<String> getVersions(String groupKey) {
    return Arrays.asList("foo", "bar");
  }

  @Test
  public void testGetVersions() throws IOException {
    Assert.assertEquals("[\"foo\",\"bar\"]", client.get("/api/getVersions/xy"));
  }
}
