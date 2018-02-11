package flask.test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.junit.Assert;

public class TestUtil {
  public static void assertFails(Callable callable, String message) {
    try {
      callable.call();
      Assert.fail("An exception was supposed to be thrown (with message: " + message + ")");
    }
    catch (Exception e) {
      Assert.assertEquals(message, e.getMessage());
    }
  }

  public static String urlEncodeMap(Map<String, String> form) {
    return form.entrySet()
               .stream()
               .map(e -> e.getKey() + "=" + encode(e.getValue()))
               .collect(Collectors.joining("&"));
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
