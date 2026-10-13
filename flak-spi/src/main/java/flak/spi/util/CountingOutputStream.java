package flak.spi.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Counts the bytes written into the stream it wraps.
 */
public class CountingOutputStream extends FilterOutputStream {

  private long count;

  public CountingOutputStream(OutputStream out) {
    super(out);
  }

  public long getCount() {
    return count;
  }

  @Override
  public void write(int b) throws IOException {
    out.write(b);
    count++;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    // FilterOutputStream would write the bytes one at a time
    out.write(b, off, len);
    count += len;
  }
}
