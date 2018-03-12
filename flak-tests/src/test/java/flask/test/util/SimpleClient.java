package flask.test.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import flak.HttpException;
import flak.spi.util.IO;

/**
 * Minimal HTTP client that mimics the behaviour of a browser, eg. by allowing
 * cookies.
 *
 * @author pcdv
 */
public class SimpleClient {

  private final String rootUrl;

  private CookieManager cookies;

  public SimpleClient(String host, int port) {
    this("http://" + host + ":" + port);
  }

  public SimpleClient(String rootUrl) {
    this.rootUrl = rootUrl;
    this.cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    // WTF: there is no non-static way to bind a CookieHandler to an
    // URLConnection!
    CookieHandler.setDefault(cookies);
  }

  /**
   * GETs data from the server at specified path.
   */
  public String get(String path) throws IOException {
    return toString(doHTTP(path, null, "GET", null));
  }

  private static String toString(InputStream in) throws IOException {
    return new String(IO.readFully(in));
  }

  /**
   * POSTs data on the server at specified path and return results as string.
   */
  public String post(String path, String data) throws IOException {
    return toString(doHTTP(path, data, "POST", null));
  }

  public String put(String path, String data) throws IOException {
    return toString(doHTTP(path, data, "PUT", null));
  }

  public void addCookie(String name, String value) {
    HttpCookie cookie = new HttpCookie(name, value);
    cookie.setPath("/");
    cookie.setVersion(0);
    cookies.getCookieStore().add(URI.create(rootUrl), cookie);
  }

  public HttpCookie getCookie(String name) throws URISyntaxException {
    return cookies.getCookieStore()
                  .get(new URI(rootUrl))
                  .stream()
                  .filter(c -> c.getName().equals(name))
                  .findAny()
                  .get();
  }

  private InputStream doHTTP(String path,
                             Object data,
                             String method,
                             Map<String, List<String>> responseHeaders) throws HttpException, IOException {
    URL url = new URL(rootUrl + path);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod(method);

    if (data != null) {
      con.setDoOutput(true);

      String body = data.toString();
      addAdditionalHeaders(path, method, con, body);
      con.getOutputStream().write(body.getBytes("UTF-8"));
      con.getOutputStream().close();
    }

    if (responseHeaders != null)
      responseHeaders.putAll(con.getHeaderFields());

    // https://stackoverflow.com/questions/4633048/httpurlconnection-reading-response-content-on-403-error
    con.getResponseCode();
    InputStream err = con.getErrorStream();
    if (err != null) {
      byte[] bytes = IO.readFully(err);
      String error = new String(bytes);
      throw new HttpException(con.getResponseCode(), error);
    }
    return con.getInputStream();
  }

  protected void addAdditionalHeaders(String path,
                                      String method,
                                      HttpURLConnection con,
                                      String body) {
  }
}