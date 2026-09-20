package flak.backend.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

  /**
   * Max size of a request body. Beyond that, the aggregator answers 413.
   */
  private static final int MAX_CONTENT_LENGTH = 16 * 1024 * 1024;

  private final NettyWebServer server;
  private final NettyFlakHandler handler;

  public ServerInitializer(NettyWebServer server) {
    this.server = server;
    this.handler = new NettyFlakHandler(server);
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    SSLContext sslContext = server.getSSLContext();
    if (sslContext != null) {
      SSLEngine engine = sslContext.createSSLEngine();
      engine.setUseClientMode(false);
      ch.pipeline().addLast(new SslHandler(engine));
    }

    ch.pipeline()
      .addLast(new HttpServerCodec())
      // gathers the body so that the handler receives a FullHttpRequest
      .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
      .addLast(handler)
//      .addLast(badClientSilencer)
    ;
  }
}
