package flask.test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import flak.annotations.Route;
import flak.backend.jdk.RouteDumper;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class RouteDumperTest extends AbstractAppTest {

  @Route("/a")
  public void getA() {
  }

  @Route("/b")
  public void getB() {
  }

  @Test
  public void testIt() {
    StringBuilder s = new StringBuilder();
    new RouteDumper().dumpRoutes(app, s);
    Assert.assertEquals(

      Stream.of("/a:",
                "/a GET getA flask.test.RouteDumperTest",
                "",
                "/b:",
                "/b GET getB flask.test.RouteDumperTest")
            .collect(Collectors.joining("\n")),
      s.toString().trim().replaceAll("  *", " "));
  }
}
