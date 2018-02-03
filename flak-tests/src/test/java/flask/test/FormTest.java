package flask.test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import flak.Form;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class FormTest extends AbstractAppTest {

  @Post
  @Route("/api/form")
  public String postForm(Form form) {
    return "Hello " + form.get("name");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testFormData() throws IOException {
    Map<String, String> form = new HashMap() {
      {
        put("foo", " 1 2 3 !");
        put("name", " w o r l d !");
        put("bar", "#&=!");
      }
    };
    Assert.assertEquals("Hello  w o r l d !",
                        client.post("/api/form", TestUtil.urlEncodeMap(form)));
  }
}
