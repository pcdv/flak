package flask.test;

import flak.Form;
import flak.Query;
import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * Misc POST tests.
 */
public class PostTest extends AbstractAppTest {

  @Post
  @Route("/form")
  public String postLogin(Form form) {
    return app.getRequest().getForm().get("login");
  }

  /**
   * Checks that we can retrieve data from an URL encoded form.
   */
  @Test
  public void testPostForm() throws Exception {
    Assert.assertEquals("bart",
                        client.post("/form", "foo=bar&login=bart&bar=foo"));
  }

  @Post
  @Route("/api/stuff/:id")
  public String postFormWithQueryAndArgs(Form form, Query query, String id) {
    return form.get("name") + " " + query.get("mode") + " " + id;
  }

  /**
   * Check that we can mix Form, Query and String in same method.
   */
  @Test
  public void testPostFormWithQueryAndArg() throws Exception {
    Assert.assertEquals("bart foo 42",
                        client.post("/api/stuff/42?mode=foo",
                                    "foo=bar&name=bart&bar=foo"));
  }

}
