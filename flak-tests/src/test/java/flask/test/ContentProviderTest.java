package flask.test;

import flak.plugin.resource.DefaultContentTypeProvider;
import org.junit.Assert;
import org.junit.Test;

public class ContentProviderTest {
  @Test
  public void miscTests() {
    DefaultContentTypeProvider p = new DefaultContentTypeProvider();
    Assert.assertEquals("image/svg+xml", p.getContentType("FOO.SVG"));
  }
}
