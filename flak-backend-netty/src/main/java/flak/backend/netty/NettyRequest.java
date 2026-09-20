package flak.backend.netty;

import flak.Form;
import flak.Query;
import flak.spi.FormImpl;
import flak.spi.SPRequest;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class NettyRequest implements SPRequest {
  final NettyMethodHandler handler;
  final FullHttpRequest req;
  private final ChannelHandlerContext ctx;
  private final String[] split;
  private final NettyResponse response;

  /**
   * Path and query string, percent-decoded but with '+' left alone, exactly
   * like HttpExchange.getRequestURI() gives them to the JDK backend.
   */
  private final String path, queryString;

  private Form form;

  public NettyRequest(NettyMethodHandler handler, ChannelHandlerContext ctx, FullHttpRequest req) {
    this.handler = handler;
    this.ctx = ctx;
    this.req = req;

    URI uri = URI.create(req.uri());
    this.path = uri.getPath();
    this.queryString = uri.getQuery();

    String[] split = path.split("/");
    this.split = Arrays.copyOfRange(split, handler.route.level + 1, split.length);
    this.response = new NettyResponse(this);
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
  public String getQueryString() {
    return queryString;
  }

  @Override
  public String getMethod() {
    return req.method().name();
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
  public NettyResponse getResponse() {
    return response;
  }

  @Override
  public String getCookie(String name) {
    // FIXME: crude, approximate implementation, not cached
    Set<Cookie> cookies;
    String value = req.headers().get(HttpHeaderNames.COOKIE);
    if (value == null) {
      cookies = Collections.emptySet();
    } else {
      cookies = ServerCookieDecoder.STRICT.decode(value);
    }
    return cookies.stream().filter(c -> c.name().equals(name)).map(Cookie::value).findAny().orElse(null);
  }

  @Override
  public Method getHandler() {
    return handler.getJavaMethod();
  }

  @Override
  public String[] getSplitUri() {
    return split;
  }

  @Override
  public String getSplit(int tokenIndex) {
    return split[tokenIndex];
  }

  // copied from JdkRequest...
  @Override
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
    // ignored
  }
}
