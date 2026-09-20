package flak.backend.netty;

import flak.Request;
import flak.spi.SPResponse;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;

public class NettyResponse implements SPResponse {
  private final NettyRequest req;
  private final DefaultHttpHeaders headers;
  private int status = 200;
  private boolean statusSet;
  private boolean compressionAllowed;

  /**
   * The body is buffered until the handler is done, then sent in one
   * FullHttpResponse. Chunked streaming remains to be done.
   */
  private final ByteArrayOutputStream body = new ByteArrayOutputStream(256);

  /**
   * Same stream, unless something wrapped it, e.g. to compress it.
   */
  private OutputStream out = body;

  public NettyResponse(NettyRequest req) {
    this.req = req;
    this.headers = new DefaultHttpHeaders();
  }

  @Override
  public Request getRequest() {
    return req;
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

  @Override
  public OutputStream getOutputStream() {
    return out;
  }

  @Override
  public void setOutputStream(OutputStream out) {
    this.out = out;
  }

  @Override
  public void redirect(String location) {
    addHeader("Location", req.handler.app.absolutePath(location));
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

  public int getStatus() {
    return status;
  }

  public HttpResponse toHttpResponse() {
    try {
      // a wrapping stream (e.g. gzip) only writes its trailer when closed
      out.close();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
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
