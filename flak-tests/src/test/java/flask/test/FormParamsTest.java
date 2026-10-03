package flask.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import flak.App;
import flak.AppFactory;
import flak.Form;
import flak.annotations.FormParams;
import flak.annotations.Post;
import flak.annotations.QueryParams;
import flak.annotations.Route;
import flak.jackson.JSON;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Objects built from the fields of a posted form with @FormParams, which
 * flak-jackson binds as it binds @QueryParams.
 */
public class FormParamsTest extends AbstractAppTest {

  public static class Signup {
    @JsonProperty(required = true)
    public String email;
    public int age = 18;
    public List<String> topic;
    public boolean newsletter;

    @Override
    public String toString() {
      return email + " " + age + " " + topic + " " + newsletter;
    }
  }

  public static class DryRun {
    public boolean dryRun;
  }

  @Route("/signup")
  @Post
  public String signup(@FormParams Signup signup) {
    return signup.toString();
  }

  @Route("/signup2")
  @Post
  public String signupWithQuery(@FormParams Signup signup, @QueryParams DryRun opts) {
    return signup.email + " " + opts.dryRun;
  }

  @Test
  public void testFields() throws Exception {
    assertEquals("a@b.c 30 [x, y] true",
                 client.post("/signup", "email=a%40b.c&age=30&topic=x&topic=y&newsletter=true"));
  }

  @Test
  public void testDefaultsAndUnknown() throws Exception {
    assertEquals("a 18 null false", client.post("/signup", "email=a&age=&other=1"));
  }

  @Test
  public void testDecoded() throws Exception {
    assertEquals("a b&c 18 null false", client.post("/signup", "email=a+b%26c"));
  }

  @Test
  public void testInvalid() throws Exception {
    TestUtil.assertFails(() -> client.post("/signup", "age=1"),
                         "400 Missing form field email");
    TestUtil.assertFails(() -> client.post("/signup", "email=a&age=old"),
                         "400 Invalid value for form field age: old");
  }

  @Test
  public void testWithQueryParams() throws Exception {
    assertEquals("a true", client.post("/signup2?dryRun=true", "email=a"));
  }

  @Test
  public void testRequiresJackson() throws Exception {
    AppFactory factory = TestUtil.getFactory();
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    factory.setPlugins();
    App bare = factory.createApp();
    try {
      TestUtil.assertFails(() -> bare.scan(new Object() {
        @Route("/signup")
        @Post
        public String signup(@FormParams Signup signup) {
          return "";
        }
      }), "Method signup() uses @FormParams, which requires flak-jackson");
    }
    finally {
      bare.stop();
    }
  }

  @Test
  public void testOnlyOneBody() {
    TestUtil.assertFails(() -> app.scan(new Object() {
      @Route("/twice")
      @Post
      public String twice(@FormParams Signup signup, Form form) {
        return "";
      }
    }), "Method twice() reads the body with @FormParams, and cannot read it again");
    TestUtil.assertFails(() -> app.scan(new Object() {
      @Route("/json")
      @Post
      @JSON
      public String json(@FormParams Signup signup, DryRun body) {
        return "";
      }
    }), "Method json() reads the body with @FormParams, and cannot read it again");
  }
}
