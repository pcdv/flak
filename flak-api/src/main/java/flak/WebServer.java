package flak;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;

/**
 * @author pcdv
 */
public interface WebServer {
  /**
   * Experimental. Enables HTTPS.
   */
  void setSSLContext(SSLContext context);

  void start() throws IOException;

  void stop();

  int getPort();

  String getProtocol();

  String getHostName();

  void setHostName(String hostName);

  void setExecutor(ExecutorService executor);
}
