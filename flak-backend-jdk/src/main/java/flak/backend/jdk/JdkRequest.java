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
import flak.spi.AbstractApp;
import flak.spi.FormImpl;
import flak.Request;
import flak.Response;
import flak.spi.SPRequest;
import flak.spi.SPResponse;
import flak.spi.util.BufferedOutputStream;
import flak.annotations.MaxBodySize;
import flak.HttpException;
import flak.spi.util.IO;
import flak.spi.util.LimitedInputStream;

public class JdkRequest implements SPRequest, SPResponse {

  private static final String[] EMPTY = {};

  private final App app;

  private final HttpExchange exchange;

  private final String[] split;

  private final String qs;

  private final String appRelativePath;

  private OutputStream outputStream;

  private Form form;
  /**
   * The limit that applies to this request, set by the handler serving it.
   * Negative means no limit.
   */
  private long maxBodySize = MaxBodySize.UNLIMITED;

  private final HeaderList headers = new HeaderList();
  private int status;
  private boolean statusFlushed;

  private Method handler;
  private boolean compressionAllowed;

  public JdkRequest(App app,
                    String appRelativePath,
                    String contextRelativePath,
                    HttpExchange r) {
    this.app = app;
    this.exchange = r;
    // raw: decoded, a value containing an encoded '&' could no longer be told
    // from two parameters
    this.qs = r.getRequestURI().getRawQuery();
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

  @SuppressWarnings("StatementWithEmptyBody")
  public String getSplat(int slashCount) {
    int pos = 0;
    for (int i = 0; i < slashCount ; pos = appRelativePath.indexOf('/', pos + 1), i++);
    return appRelativePath.substring(pos + 1);
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
    return new FormImpl(getQueryString(), true);
  }

  @Override
  public String getHeader(String name) {
    return getExchange().getRequestHeaders().getFirst(name);
  }

  @Override
  public Form getForm() {
    try {
      if (form == null)
        form = new FormImpl(readData(), true);
    }
    catch (IOException e) {
      // NB: not any exception, which would turn the HttpException of an
      // oversized body (413) or of malformed data (400) into a 500
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
    return LimitedInputStream.limit(exchange.getRequestBody(), maxBodySize);
  }

  /**
   * Rejects the request straight away when it announces a body bigger than
   * the handler accepts: no point reading megabytes only to refuse them.
   */
  @Override
  public void setMaxBodySize(long maxBodySize) {
    this.maxBodySize = maxBodySize;

    if (maxBodySize >= 0) {
      String len = getHeader("Content-Length");
      if (len != null) {
        try {
          if (Long.parseLong(len.trim()) > maxBodySize)
            throw new HttpException(413,
                                    "Request body exceeds the maximum of " +
                                    maxBodySize + " bytes");
        }
        catch (NumberFormatException ignored) {
          // a malformed length is the body reader's problem, not ours
        }
      }
    }
  }


  // /////////// Response methods

  public void addHeader(String header, String value) {
    if (statusFlushed)
      throw new IllegalStateException("Status and headers already sent");
    headers.add(header, value);
  }

  @Override
  public boolean hasResponseHeader(String name) {
    return headers.has(name);
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
        public void close() throws IOException {
          super.flush();
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
    addHeader("Location", ((AbstractApp) app).redirectLocation(location));
    setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
  }

  @Override
  public void setCompressionAllowed(boolean compressionAllowed) {
    this.compressionAllowed = compressionAllowed;

  }

  @Override
  public boolean isCompressionAllowed() {
    return compressionAllowed;
  }

  void finish() throws IOException {
    flushStatus();
    if (outputStream != null) {
      outputStream.close();
    }
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

  /**
   * com.sun.net.httpserver aborts the exchange, closing the connection without
   * terminating the chunked body, when the serving thread is interrupted.
   */
  @Override
  public void abort() {
    Thread.currentThread().interrupt();
  }

  @Override
  public boolean hasOutputStream() {
    return outputStream != null;
  }

  @Override
  public void setOutputStream(OutputStream out) {
    this.outputStream = out;
  }
}
