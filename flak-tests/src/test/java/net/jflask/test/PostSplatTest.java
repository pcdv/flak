package net.jflask.test;

import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Reproduced an ArrayIndexOutOfBoundsException.
 */
public class PostSplatTest extends AbstractAppTest {

  @Route(value = "/process/:id/file/*splat", method = "POST")
  public String handlePostWithSplat(String id, String splat) {
    return id + ":" + splat;
  }

  @Test
  public void testPostWithSplat() throws Exception {
    assertEquals("42:a/b/c", client.post("/process/42/file/a/b/c", "foo"));
  }

}
