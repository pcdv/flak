package flak.util;

import java.io.ByteArrayOutputStream;
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
   * Reads input stream until EOF and writes all read data into output stream,
   * then closes it.
   */
  public static void pipe(InputStream in, OutputStream out, boolean closeOutput)
      throws IOException {
    byte[] buf = new byte[1024];
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
  }

}
