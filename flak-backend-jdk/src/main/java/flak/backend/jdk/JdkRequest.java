package flak.backend.jdk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.List;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import flak.App;
import flak.Form;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.spi.SPRequest;
import flak.spi.util.IO;

public class JdkRequest implements SPRequest, Response {

  private static final String[] EMPTY = {};

  private final App app;

  private final HttpExchange exchange;

  private final String[] split;

  private final String qs;

  private final String appRelativePath;

  private BufferedOutputStream outputStream;

  private Form form;
  private HeaderList headers = new HeaderList();
  private int status;
  private boolean statusFlushed;

  private Method handler;

  public JdkRequest(App app,
                    String appRelativePath,
                    String contextRelativePath,
                    HttpExchange r) {
    this.app = app;
    this.exchange = r;
    this.qs = r.getRequestURI().getQuery();
    this.appRelativePath = appRelativePath;
    this.split = (contextRelativePath.isEmpty() || contextRelativePath.equals("/"))
      ? EMPTY
      : trimLeftSlash(contextRelativePath).split("/");
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return exchange.getRemoteAddress();
  }

  public String getSplit(int tokenIndex) {
    return split[tokenIndex];
  }

  public String getSplat(int tokenIndex) {
    // TODO directly return a substring of the path
    StringBuilder b = new StringBuilder(64);
    for (int i = tokenIndex; i < split.length; i++) {
      if (b.length() > 0)
        b.append('/');
      b.append(split[i]);
    }
    return b.toString();
  }

  @Override
  public void setHandler(Method handler) {
    this.handler = handler;
  }

  @Override
  public String[] getSplitUri() {
    return split;
  }

  private static String trimLeftSlash(String uri) {
    if (uri.startsWith("/"))
      return uri.substring(1);
    else
      return uri;
  }

  public String getPath() {
    return appRelativePath;
  }

  public String getQueryString() {
    return qs;
  }

  public HttpExchange getExchange() {
    return exchange;
  }

  public String getMethod() {
    return exchange.getRequestMethod();
  }

  @Override
  public Query getQuery() {
    return new FormImpl(getQueryString());
  }

  @Override
  public String getHeader(String name) {
    return getExchange().getRequestHeaders().getFirst(name);
  }

  @Override
  public Form getForm() {
    try {
      if (form == null)
        form = new FormImpl(readData());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return form;
  }

  @Override
  public Response getResponse() {
    return this;
  }

  @Override
  public Request getRequest() {
    return this;
  }

  public Method getHandler() {
    return handler;
  }

  @Override
  public String getCookie(String name) {
    Headers headers = exchange.getRequestHeaders();
    if (headers != null) {
      List<String> cookies = headers.get("Cookie");
      if (cookies != null) {
        for (String cookieString : cookies) {
          String[] tokens = cookieString.split("\\s*;\\s*");
          for (String token : tokens) {
            if (token.startsWith(name) && token.charAt(name.length()) == '=') {
              return token.substring(name.length() + 1);
            }
          }
        }
      }
    }
    return null;
  }

  private String readData() throws IOException {
    return new String(IO.readFully(getInputStream()));
  }

  public InputStream getInputStream() {
    return exchange.getRequestBody();
  }

  @Override
  public String getHttpMethod() {
    return exchange.getRequestMethod();
  }

  // /////////// Response methods

  public void addHeader(String header, String value) {
    if (statusFlushed)
      throw new IllegalStateException("Status and headers already sent");
    headers.add(header, value);
  }

  public void setStatus(int status) {
    if (statusFlushed && status != this.status)
      throw new IllegalStateException("Status has already been sent: " + this.status);
    this.status = status;
  }

  @Override
  public boolean isStatusSet() {
    return status != 0;
  }

  public OutputStream getOutputStream() {
    if (outputStream == null) {
      this.outputStream = new BufferedOutputStream(exchange.getResponseBody(), 8192) {
        @Override
        public void close() {
          // disable close, we will do it in finish()
        }

        @Override
        protected void flushBuffer() throws IOException {
          flushStatus();
          super.flushBuffer();
        }
      };
    }
    return outputStream;
  }

  @Override
  public void redirect(String location) {
    addHeader("Location", app.absolutePath(location));
    setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
  }

  void finish() throws IOException {
    flushStatus();
    if (outputStream != null)
      outputStream.flush();
    exchange.getResponseBody().close();
  }

  private void flushStatus() throws IOException {
    if (!statusFlushed) {
      statusFlushed = true;
      if (status == 0)
        status = 200;
      headers.addHeadersInto(exchange);
      long responseLength = "HEAD".equals(exchange.getRequestMethod()) ? -1 : 0;
      exchange.sendResponseHeaders(status, responseLength);
    }
  }

  boolean hasOutputStream() {
    return outputStream != null;
  }
}
