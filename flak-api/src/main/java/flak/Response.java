package flak;

import java.io.OutputStream;

public interface Response {

  /**
   * Returns the associated request.
   */
  Request getRequest();

  void addHeader(String header, String value);

  /**
   * Checks whether specified response header is set.
   */
  boolean hasResponseHeader(String name);

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

  /**
   * Configures whether gzip compression can be applied automatically according
   * to Accept-Encoding request header and absence of Content-Encoding response
   * header (false by default).
   */
  void setCompressionAllowed(boolean compressionAllowed);

  /**
   * @see #setCompressionAllowed(boolean)
   */
  boolean isCompressionAllowed();
}
