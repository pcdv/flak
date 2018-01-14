package net.jflask.test;

import flak.annotations.Post;
import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Misc POST tests.
 */
public class PostTest extends AbstractAppTest {

  @Post
  @Route("/form")
  public String postLogin() {
    return app.getRequest().getForm("login");
  }

  /**
   * Checks that we can retrieve data from an URL encoded form.
   */
  @Test
  public void testPostForm() throws Exception {
    assertEquals("bart", client.post("/form", "foo=bar&login=bart&bar=foo"));
  }
}
