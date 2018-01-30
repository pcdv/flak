package net.jflask.test;

import java.util.concurrent.Callable;

import org.junit.Assert;

public class TestUtil {
  public static void assertFails(Callable callable, String message) {
    try {
      callable.call();
      Assert.fail("Should have failed");
    }
    catch (Exception e) {
      Assert.assertEquals(message, e.getMessage());
    }
  }
}
