package flak;

import java.io.OutputStream;

public interface Response {

  void addHeader(String header, String value);

  /**
   * Warning: must be called after addHeader().
   *
   * @param status
   * @see java.net.HttpURLConnection
   */
  void setStatus(int status);

  OutputStream getOutputStream();

  void redirect(String path);
}
