package flask.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import flak.App;
import flak.AppFactory;
import flak.annotations.Post;
import flak.annotations.QueryParam;
import flak.annotations.QueryParams;
import flak.annotations.Route;
import flak.jackson.JSON;
import flak.jackson.JacksonPlugin;
import org.junit.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Objects built from the whole query string with @QueryParams, which
 * flak-jackson binds.
 */
public class QueryParamsTest extends AbstractAppTest {

  public enum Color {RED, GREEN}

  public static class Search {
    public String q;
    public int limit = 50;
    public List<String> tag;
    public Color color;
    public boolean verbose;

    @Override
    public String toString() {
      return q + " " + limit + " " + tag + " " + color + " " + verbose;
    }
  }

  public record Range(@JsonProperty(required = true) int from, Integer to) {
  }

  public static class User {
    @JsonProperty("user.name")
    public String userName;
  }

  public static class Item {
    public String name;
  }

  public static class DryRun {
    public boolean dryRun;
  }

  /**
   * An annotation of the application, unknown to Flak and Jackson, like
   * those psrv puts on the requests its CLI sends.
   */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Opt {
    String name();
  }

  public static class Deploy {
    @Opt(name = "proc")
    public String processId;
  }

  /**
   * Names the properties after @Opt, so that Deploy needs no other
   * annotation.
   */
  public static class OptIntrospector extends JacksonAnnotationIntrospector {
    @Override
    public PropertyName findNameForDeserialization(Annotated a) {
      Opt opt = a.getAnnotation(Opt.class);
      return opt != null ? PropertyName.construct(opt.name())
                         : super.findNameForDeserialization(a);
    }
  }

  @Override
  protected void preScan() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setAnnotationIntrospector(new OptIntrospector());
    JacksonPlugin.get(app).registerMapper("OPT", mapper);
  }

  @Route("/search")
  public String search(@QueryParams Search search) {
    return search.toString();
  }

  @Route("/range")
  public String range(@QueryParams Range range) {
    return range.from() + "-" + range.to();
  }

  @Route("/user")
  public String user(@QueryParams User user) {
    return user.userName;
  }

  @Route("/items")
  @Post
  public String create(@JSON Item item, @QueryParams DryRun opts) {
    return item.name + " " + opts.dryRun;
  }

  @Route("/mixed")
  public String mixed(@QueryParams Search search, @QueryParam("q") String q) {
    return search.q + " " + q;
  }

  @Route("/deploy")
  @JSON("OPT")
  public String deploy(@QueryParams Deploy deploy) {
    return deploy.processId;
  }

  @Test
  public void testFields() throws Exception {
    assertEquals("shoes 20 [a, b] RED true",
                 client.get("/search?q=shoes&limit=20&tag=a&tag=b&color=RED&verbose=true"));
  }

  @Test
  public void testAbsent() throws Exception {
    // the values the fields are initialized with
    assertEquals("null 50 null null false", client.get("/search"));
  }

  @Test
  public void testSingleValueInList() throws Exception {
    assertEquals("null 50 [a] null false", client.get("/search?tag=a"));
  }

  @Test
  public void testEmpty() throws Exception {
    // an empty string is a value, an empty number is not
    assertEquals(" 50 null null false", client.get("/search?q=&limit=&color=&verbose="));
  }

  @Test
  public void testUnknownIgnored() throws Exception {
    assertEquals("a 50 null null false", client.get("/search?q=a&_=123&foo=bar"));
  }

  @Test
  public void testDecoded() throws Exception {
    assertEquals("a b&c 50 null null false", client.get("/search?q=a+b%26c"));
  }

  @Test
  public void testInvalid() throws Exception {
    TestUtil.assertFails(() -> client.get("/search?limit=abc"),
                         "400 Invalid value for query parameter limit: abc");
    TestUtil.assertFails(() -> client.get("/search?color=BLUE"),
                         "400 Invalid value for query parameter color: BLUE");
    TestUtil.assertFails(() -> client.get("/search?verbose=yes"),
                         "400 Invalid value for query parameter verbose: yes");
    // a single value expected
    TestUtil.assertFails(() -> client.get("/search?q=a&q=b"),
                         "400 Invalid value for query parameter q: [\"a\",\"b\"]");
  }

  @Test
  public void testRecord() throws Exception {
    assertEquals("1-5", client.get("/range?from=1&to=5"));
    assertEquals("1-null", client.get("/range?from=1"));
    TestUtil.assertFails(() -> client.get("/range?to=5"), "400 Missing query parameter from");
    TestUtil.assertFails(() -> client.get("/range?from=&to=5"),
                         "400 Missing query parameter from");
  }

  @Test
  public void testRenamed() throws Exception {
    assertEquals("bob", client.get("/user?user.name=bob"));
  }

  @Test
  public void testWithBody() throws Exception {
    assertEquals("x true", client.post("/items?dryRun=true", "{\"name\":\"x\"}"));
    assertEquals("x false", client.post("/items", "{\"name\":\"x\"}"));
  }

  @Test
  public void testWithQueryParam() throws Exception {
    assertEquals("a a", client.get("/mixed?q=a"));
  }

  @Test
  public void testMapperOfHandler() throws Exception {
    // @JSON also writes the result, as a JSON string
    assertEquals("\"p1\"", client.get("/deploy?proc=p1"));
  }

  @Test
  public void testRequiresJackson() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    factory.setPlugins();
    App bare = factory.createApp();
    try {
      TestUtil.assertFails(() -> bare.scan(new Object() {
        @Route("/search")
        public String search(@QueryParams Search search) {
          return "";
        }
      }), "Method search() uses @QueryParams, which requires flak-jackson");
    }
    finally {
      bare.stop();
    }
  }

  @Test
  public void testBothAnnotations() {
    TestUtil.assertFails(() -> app.scan(new Object() {
      @Route("/both")
      public String both(@QueryParams @QueryParam("s") Search s) {
        return "";
      }
    }), "can only have one of @QueryParam, @QueryParams and @FormParams");
  }
}
