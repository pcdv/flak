package flak.backend.netty;

import flak.Form;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.spi.FormImpl;
import flak.spi.SPRequest;
import flak.spi.SPResponse;
import flak.spi.util.BufferedOutputStream;
import flak.spi.util.Log;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

  private static final int BUFFER_SIZE = 8192;



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
   * Buffers the response until it is clear whether it fits in one message. Null
   * until a handler asks for the output stream.
   */
  private ResponseStream responseStream;

  /**
   * What handlers write into: the response stream, or something wrapping it,
   * e.g. a GZIPOutputStream.
   */
  private OutputStream out;

  /**
   * True once the headers went out, which commits the response to chunked
   * encoding: the status and the headers can no longer change.
   */
  private boolean headersSent;

  private boolean aborted;

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
      out = responseStream = new ResponseStream();
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
  public void abort() {
    aborted = true;
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

  /**
   * Sends whatever is left of the response and closes the connection. A
   * response small enough to have stayed in the buffer is sent in one message,
   * with a Content-Length; a response already being streamed is terminated
   * with an empty last chunk.
   */
  public void finish() {
    if (out != null && out != responseStream) {
      try {
        // a wrapping stream, e.g. gzip, only writes its trailer when closed
        out.close();
      }
      catch (IOException e) {
        Log.error("Could not close the response stream", e);
        aborted = true;
      }
    }

    if (aborted) {
      // close without the terminating chunk, so that the client sees a broken
      // response rather than a truncated but valid one
      ctx.close();
      return;
    }

    if (headersSent) {
      try {
        responseStream.flush();
      }
      catch (IOException e) {
        ctx.close();
        return;
      }
      ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
         .addListener(ChannelFutureListener.CLOSE);
      return;
    }

    ByteBuf content = responseStream == null
      ? Unpooled.EMPTY_BUFFER
      : Unpooled.wrappedBuffer(responseStream.drain());

    FullHttpResponse r =
      new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                  HttpResponseStatus.valueOf(status),
                                  content);
    r.headers().add(headers);
    r.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
    r.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    ctx.writeAndFlush(r).addListener(ChannelFutureListener.CLOSE);
  }

  /**
   * Sends the headers, which switches the response to chunked encoding. Called
   * as soon as the buffer must be emptied, i.e. when the handler flushes or
   * writes more than the buffer holds.
   */
  private void beginStreaming() {
    if (headersSent)
      return;
    headersSent = true;

    HttpResponse r = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                             HttpResponseStatus.valueOf(status));
    r.headers().add(headers);
    r.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    HttpUtil.setTransferEncodingChunked(r, true);
    ctx.writeAndFlush(r);
  }

  /**
   * Everything that leaves the buffer is a chunk: reaching this stream at all
   * means the response no longer fits in one message.
   */
  private final OutputStream chunks = new OutputStream() {

    @Override
    public void write(int b) throws IOException {
      write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      if (len == 0)
        return;

      beginStreaming();

      // wait for the chunk to reach the socket, so that a handler producing
      // faster than the client consumes is slowed down rather than piling up
      ctx.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(b, off, len)))
         .awaitUninterruptibly();
    }
  };

  /**
   * Buffers the response, and turns it into chunks once it no longer fits.
   */
  private class ResponseStream extends BufferedOutputStream {

    ResponseStream() {
      super(chunks, BUFFER_SIZE);
    }

    /**
     * Takes back what is still buffered, i.e. the whole response when it never
     * grew past the buffer.
     */
    byte[] drain() {
      byte[] res = Arrays.copyOf(buf, count);
      count = 0;
      return res;
    }

    @Override
    public void close() throws IOException {
      // the connection is closed by finish(), not here
      flush();
    }
  }
}
