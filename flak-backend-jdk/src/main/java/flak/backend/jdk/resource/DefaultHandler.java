package flak.backend.jdk.resource;

import java.io.IOException;
import java.io.PrintStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import flak.RequestHandler;
import flak.backend.jdk.JdkApp;
import flak.util.Log;

public abstract class DefaultHandler implements HttpHandler, RequestHandler {

  protected final JdkApp app;

  public DefaultHandler(JdkApp app) {
    this.app = app;
  }

  @Override
  public JdkApp getApp() {
    return app;
  }

  public void handle(HttpExchange r) throws IOException {
    try {
      doHandle(r);
    }
    catch (Throwable t) {
      Log.error("Error occurred", t);
      r.sendResponseHeaders(500, 0);
      r.getResponseBody().write("Internal Server Error".getBytes());
      if (Log.DEBUG)
        t.printStackTrace(new PrintStream(r.getResponseBody()));
    } finally {
      r.getResponseBody().close();
    }
  }

  private void doHandle(HttpExchange r) throws Exception {

    Log.debug(r.getRequestURI());

    switch (r.getRequestMethod()) {
    case "GET":
      doGet(r);
      break;
    default:
      throw new RuntimeException("Invalid method: " + r.getRequestMethod());
    }
  }

  public abstract void doGet(HttpExchange r) throws Exception;
}
