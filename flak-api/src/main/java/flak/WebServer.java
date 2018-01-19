package flak;

import java.io.IOException;

/**
 * @author pcdv
 */
public interface WebServer {
  void start() throws IOException;
  void stop();
  int getPort();
}
