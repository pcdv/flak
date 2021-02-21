package flak.backend.netty;

import flak.Request;
import flak.Response;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class NettyResponse implements Response {
  private final NettyRequest req;
  private final DefaultHttpHeaders headers;
  private int status = 200;

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
  public void setStatus(int status) {
    this.status = status;
  }

  @Override
  public boolean isStatusSet() {
    return false;
  }

  @Override
  public OutputStream getOutputStream() {
    throw new RuntimeException("TODO");
  }

  @Override
  public void redirect(String location) {
    addHeader("Location", req.handler.app.absolutePath(location));
    setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
  }

  public int getStatus() {
    return status;
  }

  public HttpResponse toHttpResponse() {
    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                HttpResponseStatus.valueOf(status),
                                Unpooled.copiedBuffer("", StandardCharsets.UTF_8));
    throw new RuntimeException("TODO");
    //r.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
    //HttpUtil.setKeepAlive(r, false);
    //return r;
  }
}
