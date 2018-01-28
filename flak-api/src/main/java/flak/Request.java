package flak;

import java.io.InputStream;
import java.util.List;

public interface Request {

  /**
   * Returns the part of this request's URL from the protocol name up to the
   * query string in the first line of the HTTP request.
   */
  String getRequestURI();

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
   * Returns parameter submitted in the query string, eg. if URL =
   * "...?key=value", getArg("key", null) returns "value".
   *
   * @param def the default value to return if specified parameter in unset
   * @return the parameter found in the query string or specified default value
   */
  String getArg(String name, String def);

  /**
   * Returns a field from form data (only valid for POST requests).
   */
  String getForm(String field);

  /**
   * Returns a list containing all occurrences of a given parameter in query
   * string, or an empty list if none is found.
   *
   * @param name the parameter's name
   */
  List<String> getArgs(String name);

  InputStream getInputStream();
}
