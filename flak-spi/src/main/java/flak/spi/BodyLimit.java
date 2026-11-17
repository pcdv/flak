package flak.spi;

import java.util.Locale;

/**
 * The escape hatch for the size limits of request bodies: the system property
 * {@value #PROPERTY} raises every limit below it, whether it comes from the
 * app, from {@link flak.annotations.MaxBodySize} or from a handler, and never
 * lowers one.
 * <p>
 * It is meant for an emergency, when legitimate clients get 413 and the
 * application has no setting of its own for the limit: start the JVM with
 * <code>-Dflak.maxBodySize=2g</code>, or <code>-1</code> for no limit at all.
 * The value is a number of bytes, optionally followed by k, m or g (powers of
 * 1024). It is read on every request, so it can also be changed in a running
 * JVM.
 *
 * @author pcdv
 */
public final class BodyLimit {

  public static final String PROPERTY = "flak.maxBodySize";

  /** Sets the most that is drained from the unread body of a request. */
  public static final String DRAIN_PROPERTY = "flak.maxDrain";

  public static final long DEFAULT_DRAIN_LIMIT = 4L * 1024 * 1024;

  /**
   * The last value of the property and what it parsed to, so that it is not
   * parsed on every request. Replaced as a whole, never mutated.
   */
  private static volatile Cached cached = new Cached(null, null);

  private BodyLimit() {}

  /**
   * Returns the limit that applies to a request, given the one configured for
   * its handler. Negative means unlimited, in both.
   */
  public static long apply(long limit) {
    Long floor = floor();
    if (floor == null || limit < 0)
      return limit;
    if (floor < 0)
      return floor;
    return Math.max(limit, floor);
  }

  /**
   * Returns how many bytes of the request body a backend reads and drops after
   * the handler is done, when the handler left part of it unread. Closing a
   * connection with unread data makes the TCP stack reset it, which can make
   * the client lose the end of the response.
   * <p>
   * The limit is what the handler accepts, minus what it read, and never more
   * than {@value #DEFAULT_DRAIN_LIMIT} bytes, or the value of the system
   * property {@value #DRAIN_PROPERTY} (same format as {@value #PROPERTY},
   * 0 to drain nothing). Nothing is drained from a request rejected for its
   * size. What remains unread is the client's problem: the connection is
   * closed, which can make the client lose the end of the response.
   *
   * @param maxBodySize the limit that applied to the request, negative for none
   * @param bytesRead   the bytes of the body the handler read
   * @param status      the status of the response
   */
  public static long drainLimit(long maxBodySize, long bytesRead, int status) {
    if (status == 413)
      return 0;
    long cap = drainCap();
    return maxBodySize >= 0 ? Math.min(cap, Math.max(0, maxBodySize - bytesRead)) : cap;
  }

  private static long drainCap() {
    String raw = System.getProperty(DRAIN_PROPERTY);
    if (raw == null)
      return DEFAULT_DRAIN_LIMIT;
    try {
      long n = parse(raw);
      return n < 0 ? Long.MAX_VALUE : n;
    }
    catch (NumberFormatException | ArithmeticException e) {
      System.err.println("flak: ignoring invalid -D" + DRAIN_PROPERTY + "=" + raw +
                         ", expected a number of bytes, optionally followed by k, m or g");
      return DEFAULT_DRAIN_LIMIT;
    }
  }

  /**
   * The value of the property, or null if it is unset or invalid.
   */
  private static Long floor() {
    String raw = System.getProperty(PROPERTY);
    Cached c = cached;
    if (raw == null ? c.raw == null : raw.equals(c.raw))
      return c.value;

    Long value = null;
    if (raw != null) {
      try {
        value = parse(raw);
      }
      catch (NumberFormatException | ArithmeticException e) {
        // not worth failing every request for, but the operator who set it
        // expects it to work, so say why it doesn't
        System.err.println("flak: ignoring invalid -D" + PROPERTY + "=" + raw +
                           ", expected a number of bytes, optionally followed by k, m or g");
      }
    }
    cached = new Cached(raw, value);
    return value;
  }

  /**
   * Parses a size: a number of bytes, optionally followed by k, m or g, -1 for
   * unlimited.
   */
  public static long parse(String s) {
    String v = s.trim().toLowerCase(Locale.ROOT);
    if (v.endsWith("b"))
      v = v.substring(0, v.length() - 1);

    long unit = 1;
    if (!v.isEmpty()) {
      switch (v.charAt(v.length() - 1)) {
        case 'k': unit = 1L << 10; break;
        case 'm': unit = 1L << 20; break;
        case 'g': unit = 1L << 30; break;
        default: break;
      }
    }
    if (unit != 1)
      v = v.substring(0, v.length() - 1).trim();

    long n = Long.parseLong(v);
    return n < 0 ? -1 : Math.multiplyExact(n, unit);
  }

  private static final class Cached {
    final String raw;
    final Long value;

    Cached(String raw, Long value) {
      this.raw = raw;
      this.value = value;
    }
  }
}
