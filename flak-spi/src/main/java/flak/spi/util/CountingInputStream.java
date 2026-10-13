package flak.spi.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counts the bytes read, or skipped, from the stream it wraps.
 */
public class CountingInputStream extends FilterInputStream {

  private long count;

  public CountingInputStream(InputStream in) {
    super(in);
  }

  public long getCount() {
    return count;
  }

  @Override
  public int read() throws IOException {
    int b = super.read();
    if (b >= 0)
      count++;
    return b;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int n = super.read(b, off, len);
    if (n > 0)
      count += n;
    return n;
  }

  @Override
  public long skip(long n) throws IOException {
    long skipped = super.skip(n);
    if (skipped > 0)
      count += skipped;
    return skipped;
  }
}
