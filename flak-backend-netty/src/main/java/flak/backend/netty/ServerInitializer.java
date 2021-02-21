package flak.backend.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
  private final NettyFlakHandler handler;

  public ServerInitializer(NettyApp app) {
    handler = new NettyFlakHandler(app);
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    ch.pipeline()
      .addLast(new HttpServerCodec())
      .addLast(handler)
//      .addLast(badClientSilencer)
    ;
  }
}
