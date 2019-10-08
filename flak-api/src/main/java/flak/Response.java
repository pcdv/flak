package flak;

import java.io.OutputStream;

public interface Response {

  void addHeader(String header, String value);

  /**
   * Warning: must be called after addHeader().
   *
   * @see java.net.HttpURLConnection
   */
  void setStatus(int status);

  boolean isStatusSet();

  OutputStream getOutputStream();

  /**
   * Replies to current request with an HTTP redirect response with specified
   * location.
   */
  void redirect(String path);
}
