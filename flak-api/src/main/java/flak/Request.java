package flak;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public interface Request {

  /**
   * Returns the address of the remote entity invoking this request
   */
  InetSocketAddress getRemoteAddress();

  /**
   * Returns the request path, relative from the app's root.
   */
  String getPath();

  /**
   * Returns the query string contained in request URL after the path. This
   * method returns <code>null</code> if the URL does not have a query string.
   * Same as the value of the CGI variable QUERY_STRING.
   *
   * @return a <code>String</code> containing the query string or
   *         <code>null</code> if the URL contains no query string
   */
  String getQueryString();
  
  /**
   * Returns the HTTP method (GET, POST, etc.) used in the request.
   */
  String getMethod();

  /**
   * Returns an object allowing to easily parse the query string.
   */
  Query getQuery();

  String getHeader(String name);

  InputStream getInputStream();

  /**
   * Same as getMethod() :/
   * @deprecated
   */
  @Deprecated
  default String getHttpMethod() {
    return getMethod();
  }

  Form getForm();

  Response getResponse();

  String getCookie(String name);

  Method getHandler();
}
