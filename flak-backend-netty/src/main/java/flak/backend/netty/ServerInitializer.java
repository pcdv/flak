package flak.backend.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

  private final NettyWebServer server;

  public ServerInitializer(NettyWebServer server) {
    this.server = server;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    SSLContext sslContext = server.getSSLContext();
    if (sslContext != null) {
      SSLEngine engine = sslContext.createSSLEngine();
      engine.setUseClientMode(false);
      ch.pipeline().addLast(new SslHandler(engine));
    }

    // the handlers are named, so that an application can position its own
    // relative to ours, e.g. pipeline.addBefore("flak", ...)
    ch.pipeline()
      .addLast("http-codec", new HttpServerCodec())
      // NB: no HttpObjectAggregator: the body is streamed to the handler, and
      // its size is capped per app or per handler with @MaxBodySize
      .addLast("flak", server.getHttpHandler());
  }
}
