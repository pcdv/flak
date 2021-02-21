/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package flak.backend.netty;

import flak.spi.util.Log;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable
public class NettyFlakHandler extends SimpleChannelInboundHandler<HttpRequest> {

  private final NettyApp app;

  NettyFlakHandler(NettyApp app) {
    this.app = app;
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, HttpRequest req) {
    if (HttpUtil.is100ContinueExpected(req)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                    HttpResponseStatus.CONTINUE));
    }
    else {
      flushResponse(ctx, req, createResponse(ctx, req));
    }
  }

  private HttpResponse createResponse(ChannelHandlerContext ctx, HttpRequest req) {
    String uri = req.uri();
    int qs = uri.indexOf('?');
    if (qs != -1)
      uri = uri.substring(0, qs);

    Log.info("Handle request at " + uri);
    String[] tokens = uri.split("/");

    try {
      HttpResponse res = app.route(ctx, req, tokens, 1);
      if (res != null) {
        return res;
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      return getError(e);
    }

    return get404();
  }

  private DefaultFullHttpResponse get404() {
    DefaultFullHttpResponse d = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND,
      Unpooled.copiedBuffer("Not found", CharsetUtil.UTF_8)
    );
    d.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    d.headers().set(HttpHeaderNames.CONTENT_LENGTH, d.content().readableBytes());
    return d;
  }

  private DefaultFullHttpResponse getError(Exception e) {
    DefaultFullHttpResponse d = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR,
      Unpooled.copiedBuffer(e.toString(), CharsetUtil.UTF_8)
    );
    d.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    d.headers().set(HttpHeaderNames.CONTENT_LENGTH, d.content().readableBytes());
    return d;
  }

  private static void flushResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
    if (HttpUtil.isKeepAlive(req)) {
      res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      ctx.writeAndFlush(res);
    }
    else {
      ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
  }
}
