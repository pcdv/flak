package flask.test;

import java.nio.file.Files;
import java.nio.file.Path;

import flak.plugin.resource.FlakResourceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Reproduces bug in AbstractResourceHandler: the ?search= part of the URL
 * was not ignored.
 *
 * @author pcdv
 */
public class ResourceWithQueryStringTest extends AbstractAppTest {

  @Test
  public void testIt() throws Exception {
    Path dir = Files.createTempDirectory("flak");
    Files.write(dir.resolve("foo"), "Foo".getBytes());

    new FlakResourceImpl(app).servePath("/",
                                        dir.toString(),
                                        getClass().getClassLoader(),
                                        false);
    assertEquals("Foo", client.get("/foo"));
    assertEquals("Foo", client.get("/foo?q=1"));
  }
}
