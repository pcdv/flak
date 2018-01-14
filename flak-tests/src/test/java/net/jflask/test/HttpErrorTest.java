package net.jflask.test;

import flak.HttpError;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class HttpErrorTest extends AbstractAppTest {

  @Route("/200")
  public String gen200() {
    throw new HttpError(200, "Works");
  }

  @Route("/204")
  public String gen204() {
    throw new HttpError(204, "No content");
  }

  @Test
  public void testStatus200() throws Exception {
    String r = client.get("/200");
    Assert.assertEquals("Works", r);
  }

  @Test
  public void testStatus204() throws Exception {
    String r = client.get("/204");
    Assert.assertEquals("", r);
  }
}
