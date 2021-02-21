package flask.test.util;

import java.io.IOException;
import java.io.OutputStream;

public class TeeOutputStream extends OutputStream {

  private final OutputStream main;

  private final OutputStream other;

  public TeeOutputStream(OutputStream out1,
                         OutputStream out2,
                         boolean allowFailures) {
    if (out1 == null || out2 == null)
      throw new NullPointerException();

    this.main = out1;
    this.other = out2;
  }

  public TeeOutputStream(OutputStream out1, OutputStream out2) {
    this(out1, out2, false);
  }

  @Override
  public void write(int b) throws IOException {
    main.write(b);
    other.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    main.write(b, off, len);
    other.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    main.flush();
    other.flush();
  }

  @Override
  public void close() throws IOException {
    main.close();
    other.flush(); // do not close stdout
  }
}
