package flak.backend.netty;

import flak.Form;
import flak.Query;
import flak.spi.SPRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class NettyRequest implements SPRequest {
  final NettyMethodHandler handler;
  final HttpRequest req;
  private final ChannelHandlerContext ctx;
  private final String[] split;
  private final NettyResponse response;

  public NettyRequest(NettyMethodHandler handler, ChannelHandlerContext ctx, HttpRequest req) {
    this.handler = handler;
    this.ctx = ctx;
    this.req = req;
    String[] split = req.uri().split("/");
    this.split = Arrays.copyOfRange(split, handler.route.level + 1, split.length);
    this.response = new NettyResponse(this);
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return (InetSocketAddress) ctx.channel().remoteAddress();
  }

  @Override
  public String getPath() {
    return req.uri();
  }

  @Override
  public String getQueryString() {
    return "?TODO";
  }

  @Override
  public String getMethod() {
    return req.method().name();
  }

  @Override
  public Query getQuery() {
    throw new RuntimeException("TODO");
  }

  @Override
  public String getHeader(String name) {
    return req.headers().get(name);
  }

  @Override
  public InputStream getInputStream() {
    throw new RuntimeException("TODO");
  }

  @Override
  public Form getForm() {
    throw new RuntimeException("TODO");
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
