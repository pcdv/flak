package flak.spi.util;

public class Log {

  public static final boolean DEBUG = Boolean.getBoolean("debug");

  public static void warn(Object msg) {
    System.err.println("WARN: " + msg);
  }

  public static void error(Object msg) {
    System.err.println("ERROR: " + msg);
  }

  public static void error(Object msg, Throwable t) {
    System.err.println("ERROR: " + msg);
    t.printStackTrace();
  }

  public static void info(Object msg) {
    System.err.println("INFO: " + msg);
  }

  public static void debug(Object msg) {
    if (DEBUG)
      System.err.println("DEBUG: " + msg);
  }

}
