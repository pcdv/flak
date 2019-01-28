package flak.spi.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IO {

  /**
   * Reads specified input stream until it reaches EOF.
   */
  public static byte[] readFully(InputStream in) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    pipe(in, bout, false);
    return bout.toByteArray();
  }

  /**
   * Pipes input stream into output stream, reading all data until EOF is
   * encountered. The input stream is closed at the end, but for the output
   * stream this is optional.
   */
  public static void pipe(InputStream in,
                          OutputStream out,
                          boolean closeOutput) throws IOException {
    byte[] buf = new byte[1024];
    try {
      while (true) {
        int len = in.read(buf);
        if (len >= 0) {
          out.write(buf, 0, len);
          out.flush();
        }
        else {
          if (closeOutput)
            out.close();
          break;
        }
      }
    } finally {
      closeSilently(in);
    }
  }

  private static void closeSilently(Closeable closeable) {
    try {
      closeable.close();
    }
    catch (IOException ignored) {
    }
  }

}
