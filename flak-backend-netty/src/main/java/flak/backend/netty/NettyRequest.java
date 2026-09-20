package flak.backend.netty;

import flak.Form;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.spi.FormImpl;
import flak.spi.SPRequest;
import flak.spi.SPResponse;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * A request and the response being built for it. Like JdkRequest, both sides
 * are a same object: the SPI passes them around together anyway.
 */
public class NettyRequest implements SPRequest, SPResponse {

  private static final String[] EMPTY = {};

  private final NettyApp app;
  private final ChannelHandlerContext ctx;
  private final FullHttpRequest req;

  /**
   * Path of the request relative to the root of the app, without the query
   * string, and its non-empty tokens.
   */
  private final String path;
  private final String[] tokens;

  /**
   * Tokens of the path consumed by the route the request is being matched
   * against: a handler only sees what comes after its route.
   */
  private int routeLevel;
  private String[] split;

  /**
   * Percent-decoded but with '+' left alone, like the JDK backend gets it from
   * HttpExchange.getRequestURI().
   */
  private final String queryString;

  private Method handler;
  private Form form;

  private final DefaultHttpHeaders headers = new DefaultHttpHeaders();
  private int status = HttpURLConnection.HTTP_OK;
  private boolean statusSet;
  private boolean compressionAllowed;

  /**
   * The body is buffered until the handler is done, then sent as a single
   * response. Chunked streaming remains to be done.
   */
  private final ByteArrayOutputStream body = new ByteArrayOutputStream(256);
  private OutputStream out;

  public NettyRequest(NettyApp app,
                      ChannelHandlerContext ctx,
                      FullHttpRequest req,
                      String appRelativePath,
                      String queryString) {
    this.app = app;
    this.ctx = ctx;
    this.req = req;
    this.path = appRelativePath;
    this.queryString = queryString;
    this.tokens = appRelativePath.isEmpty() || appRelativePath.equals("/")
      ? EMPTY
      : trimLeftSlash(appRelativePath).split("/");
    this.split = this.tokens;
  }

  private static String trimLeftSlash(String uri) {
    return uri.startsWith("/") ? uri.substring(1) : uri;
  }

  @Override
  public Request getRequest() {
    return this;
  }

  @Override
  public Response getResponse() {
    return this;
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return (InetSocketAddress) ctx.channel().remoteAddress();
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public String getMethod() {
    return req.method().name();
  }

  @Override
  public String getQueryString() {
    return queryString;
  }

  @Override
  public Query getQuery() {
    // the query string is already decoded, do not do it twice
    return new FormImpl(queryString, false);
  }

  @Override
  public String getHeader(String name) {
    return req.headers().get(name);
  }

  @Override
  public InputStream getInputStream() {
    // duplicate so that reading the body does not consume it for getForm()
    return new ByteBufInputStream(req.content().duplicate());
  }

  @Override
  public Form getForm() {
    if (form == null)
      form = new FormImpl(req.content().toString(StandardCharsets.UTF_8), true);
    return form;
  }

  @Override
  public String getCookie(String name) {
    // FIXME: crude, approximate implementation, not cached
    Set<Cookie> cookies;
    String value = req.headers().get(HttpHeaderNames.COOKIE);
    if (value == null) {
      cookies = Collections.emptySet();
    }
    else {
      cookies = ServerCookieDecoder.STRICT.decode(value);
    }
    return cookies.stream()
                  .filter(c -> c.name().equals(name))
                  .map(Cookie::value)
                  .findAny()
                  .orElse(null);
  }

  @Override
  public Method getHandler() {
    return handler;
  }

  @Override
  public void setHandler(Method handler) {
    this.handler = handler;
  }

  /**
   * Called while walking the route tree, before handing the request to the
   * handlers of a route.
   */
  void setRouteLevel(int level) {
    if (level != routeLevel || split == tokens) {
      routeLevel = level;
      split = level <= 0 || level >= tokens.length
        ? (level <= 0 ? tokens : EMPTY)
        : Arrays.copyOfRange(tokens, level, tokens.length);
    }
  }

  @Override
  public String[] getSplitUri() {
    return split;
  }

  @Override
  public String getSplit(int tokenIndex) {
    return split[tokenIndex];
  }

  /**
   * @param slashCount number of slashes of the path that precede the splat,
   *                   counted from the root of the app
   */
  @Override
  @SuppressWarnings("StatementWithEmptyBody")
  public String getSplat(int slashCount) {
    int pos = 0;
    for (int i = 0; i < slashCount; pos = path.indexOf('/', pos + 1), i++) ;
    return pos < 0 ? "" : path.substring(pos + 1);
  }

  @Override
  public void addHeader(String header, String value) {
    headers.add(header, value);
  }

  @Override
  public boolean hasResponseHeader(String name) {
    return headers.contains(name);
  }

  @Override
  public void setStatus(int status) {
    this.status = status;
    this.statusSet = true;
  }

  @Override
  public boolean isStatusSet() {
    return statusSet;
  }

  public int getStatus() {
    return status;
  }

  @Override
  public OutputStream getOutputStream() {
    if (out == null)
      out = body;
    return out;
  }

  @Override
  public void setOutputStream(OutputStream out) {
    this.out = out;
  }

  @Override
  public boolean hasOutputStream() {
    return out != null;
  }

  @Override
  public void redirect(String location) {
    addHeader("Location", app.absolutePath(location));
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

  public HttpResponse toHttpResponse() {
    if (out != null) {
      try {
        // a wrapping stream, e.g. gzip, only writes its trailer when closed
        out.close();
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    FullHttpResponse r =
      new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                  HttpResponseStatus.valueOf(status),
                                  Unpooled.wrappedBuffer(body.toByteArray()));
    r.headers().add(headers);
    r.headers().set(HttpHeaderNames.CONTENT_LENGTH, r.content().readableBytes());
    return r;
  }
}
