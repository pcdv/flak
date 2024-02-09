package flak;

import java.io.IOException;
import java.net.InetSocketAddress;
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

  default int getPort() {
    return getLocalAddress().getPort();
  }

  InetSocketAddress getLocalAddress();

  /**
   * @return "http" or "https" depending on whether a SSLContext is in use
   */
  String getProtocol();

  /**
   * Defaults to "localhost". Can be overridden with setHostName(). Allows to
   * build URLs.
   */
  String getHostName();

  void setHostName(String hostName);

  void setExecutor(ExecutorService executor);
}
