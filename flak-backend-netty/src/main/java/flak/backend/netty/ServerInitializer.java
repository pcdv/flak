package flak.backend.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

  /**
   * Max size of a request body. Beyond that, the aggregator answers 413.
   */
  private static final int MAX_CONTENT_LENGTH = 16 * 1024 * 1024;

  private final NettyFlakHandler handler;

  public ServerInitializer(NettyWebServer server) {
    handler = new NettyFlakHandler(server);
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    ch.pipeline()
      .addLast(new HttpServerCodec())
      // gathers the body so that the handler receives a FullHttpRequest
      .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
      .addLast(handler)
//      .addLast(badClientSilencer)
    ;
  }
}
