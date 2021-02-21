package flak.backend.netty;

import flak.App;
import flak.WebServer;
import flak.spi.AbstractAppFactory;

public class NettyAppFactory extends AbstractAppFactory {
  @Override
  public WebServer getServer() {
    throw new RuntimeException("TODO");
  }

  @Override
  public App createApp() {
    throw new RuntimeException("TODO");
  }

  @Override
  public App createApp(String appRootPath) {
    throw new RuntimeException("TODO");
  }

  @Override
  public void setPort(int port) {
    throw new RuntimeException("TODO");
  }

  @Override
  public int getPort() {
    throw new RuntimeException("TODO");
  }
}
