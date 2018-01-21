package flak.backend.jdk;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import flak.RequestHandler;
import flak.WebServer;

/**
 * Wrapper for the HTTP server embedded in the JDK. It may be shared by several
 * apps.
 *
 * @author pcdv
 * @see <a
 * href="http://docs.oracle.com/javase/7/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html">Documentation
 * for HTTPServer</a>
 */
public class JdkWebServer implements WebServer {

  private HttpServer srv;

  private ExecutorService pool;

  private InetSocketAddress address;

  private final Map<String, RequestHandler> handlers = new Hashtable<>();

  private Vector<JdkApp> apps = new Vector<>();

  private SSLContext sslContext;

  private String hostName = "localhost";

  public JdkWebServer() {
  }

  @Override
  public void setSSLContext(SSLContext context) {
    this.sslContext = context;
  }

  public void addApp(JdkApp app) {
    apps.add(app);
  }

  public void removeApp(JdkApp app) {
    apps.remove(app);
    if (apps.isEmpty())
      stop();
  }

  public JdkWebServer addHandler(String path, RequestHandler handler) {
    RequestHandler old = handlers.get(path);
    if (old != null) {
      if (old.getApp() != handler.getApp())
        throw new RuntimeException(String.format(
          "Path %s already used by app %s",
          path,
          old.getApp()));
      else
        throw new RuntimeException("Path already used: " + path);
    }

    handlers.put(path, handler);
    if (srv != null)
      srv.createContext(path, (HttpHandler) handler);
    return this;
  }

  public void setPort(int port) {
    checkNotStarted();
    this.address = new InetSocketAddress(port);
  }

  private void checkNotStarted() {
    if (srv != null)
      throw new IllegalStateException("Already started");
  }

  /**
   * Shuts down the web sever.
   * <p/>
   * WARNING: with JDK6, HttpServer creates a zombie thread (blocked on a
   * sleep()). No problem with JDK 1.7.0_40.
   */
  public void stop() {
    if (srv != null)
      this.srv.stop(0);
    if (pool != null)
      pool.shutdownNow();
  }

  public int getPort() {
    return address.getPort();
  }

  @Override
  public String getProtocol() {
    return sslContext == null ? "http" : "https";
  }

  @Override
  public String getHostName() {
    return hostName;
  }

  @Override
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public void start() throws IOException {
    if (pool == null)
      pool = Executors.newCachedThreadPool();

    if (address == null)
      throw new IllegalStateException(
        "Address not set. Call AppFactory.setHttpPort()");

    this.srv = createServer();
    this.srv.setExecutor(pool);

    for (Map.Entry<String, RequestHandler> e : handlers.entrySet()) {
      String path = e.getKey();
      if (path.isEmpty())
        path = "/";
      srv.createContext(path, (HttpHandler) e.getValue());
    }

    this.srv.start();
  }

  private HttpServer createServer() throws IOException {
    if (sslContext != null)
      return createHttpsServer();
    else
      return HttpServer.create(address, 0);
  }

  private HttpServer createHttpsServer() throws IOException {
    final HttpsServer server = HttpsServer.create(address, 5);

    final HttpsConfigurator configurator = new HttpsConfigurator(sslContext) {
      @Override
      public void configure(HttpsParameters params) {
        final SSLContext context = this.getSSLContext();
        params.setSSLParameters(context.getDefaultSSLParameters());
      }
    };
    server.setHttpsConfigurator(configurator);
    return server;
  }

  public boolean isStarted() {
    return srv != null;
  }
}
