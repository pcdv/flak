package flak.spi.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import flak.HttpException;

/**
 * Fails with 413 as soon as more than the allowed number of bytes has been
 * read, so that a handler cannot be made to buffer an unbounded body just
 * because the client lied about, or omitted, its length.
 *
 * @author pcdv
 */
public class LimitedInputStream extends FilterInputStream {

  private final long max;

  private long read;

  private LimitedInputStream(InputStream in, long max) {
    super(in);
    this.max = max;
  }

  /**
   * Wraps the stream, unless the limit is negative, which means unlimited.
   */
  public static InputStream limit(InputStream in, long max) {
    return max < 0 ? in : new LimitedInputStream(in, max);
  }

  @Override
  public int read() throws IOException {
    int b = super.read();
    if (b >= 0)
      count(1);
    return b;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int n = super.read(b, off, len);
    if (n > 0)
      count(n);
    return n;
  }

  @Override
  public long skip(long n) throws IOException {
    long skipped = super.skip(n);
    if (skipped > 0)
      count(skipped);
    return skipped;
  }

  private void count(long n) {
    read += n;
    if (read > max)
      throw new HttpException(413,
                              "Request body exceeds the maximum of " + max + " bytes");
  }
}
