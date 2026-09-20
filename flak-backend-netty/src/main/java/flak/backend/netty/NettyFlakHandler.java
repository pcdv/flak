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
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.net.URI;

@ChannelHandler.Sharable
public class NettyFlakHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

  private final NettyWebServer server;

  NettyFlakHandler(NettyWebServer server) {
    this.server = server;
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
    if (HttpUtil.is100ContinueExpected(req)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                    HttpResponseStatus.CONTINUE));
      return;
    }

    // route handlers may block, and a streamed response is written by the very
    // thread that runs them, so they cannot run on an event loop
    req.retain();
    server.getExecutor().execute(() -> {
      try {
        serve(ctx, req);
      }
      finally {
        req.release();
      }
    });
  }

  private void serve(ChannelHandlerContext ctx, FullHttpRequest req) {
    URI uri;
    try {
      uri = URI.create(req.uri());
    }
    catch (IllegalArgumentException e) {
      send(ctx, HttpResponseStatus.BAD_REQUEST, "Bad request");
      return;
    }

    String path = uri.getPath();
    Log.info("Handle request at " + path);

    // several apps can be plugged at different paths on a same server
    NettyApp app = server.getApp(path);
    if (app == null) {
      send(ctx, HttpResponseStatus.NOT_FOUND, "Not found");
      return;
    }

    String appRelativePath = path.substring(app.getPath().length());
    NettyRequest r = new NettyRequest(app, ctx, req, appRelativePath, uri.getQuery());
    String[] tokens = appRelativePath.split("/");

    try {
      app.handle(r, request -> app.route(r, tokens, 1));
    }
    catch (Throwable t) {
      Log.error("Could not serve " + path, t);
    }

    r.finish();
  }

  private static void send(ChannelHandlerContext ctx,
                           HttpResponseStatus status,
                           String message) {
    DefaultFullHttpResponse d =
      new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                  status,
                                  Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
    d.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    d.headers().set(HttpHeaderNames.CONTENT_LENGTH, d.content().readableBytes());
    ctx.writeAndFlush(d).addListener(ChannelFutureListener.CLOSE);
  }
}
