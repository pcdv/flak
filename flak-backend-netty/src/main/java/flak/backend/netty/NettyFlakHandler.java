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

import java.net.URI;

import flak.spi.util.Log;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

/**
 * Feeds the requests decoded by netty to flak.
 * <p>
 * The body is not waited for: the handler is dispatched as soon as the request
 * line and the headers are in, and reads the body as it arrives. An
 * HttpObjectAggregator in front of us is supported all the same, a
 * FullHttpRequest being both the request and its single chunk of content, and
 * is what the websocket handshake needs.
 */
@ChannelHandler.Sharable
public class NettyFlakHandler extends ChannelInboundHandlerAdapter {

  /**
   * The body of the request being served on a channel. A channel serves one
   * request at a time: flak closes the connection with the response.
   */
  private static final AttributeKey<RequestBodyStream> BODY =
    AttributeKey.valueOf(NettyFlakHandler.class, "body");

  private final NettyWebServer server;

  NettyFlakHandler(NettyWebServer server) {
    this.server = server;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    try {
      if (msg instanceof HttpRequest)
        begin(ctx, (HttpRequest) msg);

      if (msg instanceof HttpContent) {
        RequestBodyStream body = ctx.channel().attr(BODY).get();
        if (body != null) {
          body.offer(((HttpContent) msg).content());
          if (msg instanceof LastHttpContent)
            body.complete();
        }
      }
    }
    finally {
      if (msg instanceof HttpContent)
        ((HttpContent) msg).release();
    }
  }

  private void begin(ChannelHandlerContext ctx, HttpRequest req) {
    if (HttpUtil.is100ContinueExpected(req)) {
      // the aggregator used to answer this, we are on our own now
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                    HttpResponseStatus.CONTINUE));
    }

    RequestBodyStream body = new RequestBodyStream(ctx.channel());
    ctx.channel().attr(BODY).set(body);

    // route handlers may block, and a streamed response is written by the very
    // thread that runs them, so they cannot run on an event loop
    server.getExecutor().execute(() -> {
      try {
        serve(ctx, req, body);
      }
      finally {
        // whatever the handler did or did not read must not be left behind
        body.discard();
        ctx.channel().attr(BODY).set(null);
      }
    });
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    failBody(ctx, new java.io.IOException("Connection closed"));
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    failBody(ctx, cause);
    ctx.close();
  }

  /**
   * Releases a handler blocked on a body that will not arrive.
   */
  private void failBody(ChannelHandlerContext ctx, Throwable cause) {
    RequestBodyStream body = ctx.channel().attr(BODY).get();
    if (body != null)
      body.fail(cause);
  }

  private void serve(ChannelHandlerContext ctx,
                     HttpRequest req,
                     RequestBodyStream body) {
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
    NettyRequest r =
      new NettyRequest(app, ctx, req, body, appRelativePath, uri.getRawQuery());
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
