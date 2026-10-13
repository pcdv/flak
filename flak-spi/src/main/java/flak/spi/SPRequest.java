package flak.spi;

import java.lang.reflect.Method;

import flak.Request;

/**
 * @author pcdv
 */
public interface SPRequest extends Request {
  String[] getSplitUri();

  String getSplit(int tokenIndex);

  String getSplat(int tokenIndex);

  void setHandler(Method handler);

  /**
   * Records the template of the route that matched the request, returned by
   * {@link #getRoute()}.
   */
  void setRoute(String route);

  /**
   * The number of bytes of the body read so far.
   */
  long getBytesIn();

  /**
   * Sets the maximum size of the body this request is allowed to carry, as
   * decided by the handler about to serve it. A negative value means no
   * limit. Called before any argument is extracted, so that a backend can
   * reject an oversized body before reading it.
   */
  void setMaxBodySize(long maxBodySize);
}
