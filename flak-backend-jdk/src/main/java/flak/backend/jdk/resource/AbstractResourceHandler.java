package flak.backend.jdk.resource;

import java.io.FileNotFoundException;
import java.io.InputStream;

import com.sun.net.httpserver.HttpExchange;
import flak.ContentTypeProvider;
import flak.backend.jdk.JdkApp;
import flak.backend.jdk.JdkRequest;
import flak.spi.SPRequest;
import flak.util.IO;
import flak.util.Log;

/**
 * Abstract handler that Serves resources found either in the file system or
 * nested in a jar.
 *
 * @author pcdv
 */
public abstract class AbstractResourceHandler extends DefaultHandler {

  private final String rootURI;

  private final boolean requiresAuth;

  private final ContentTypeProvider mime;

  AbstractResourceHandler(JdkApp app,
                          ContentTypeProvider mime,
                          String rootURI,
                          boolean requiresAuth) {
    super(app);
    this.mime = mime;
    this.rootURI = rootURI;
    this.requiresAuth = requiresAuth;
  }

  @Override
  public void doGet(HttpExchange t) throws Exception {

    app.setThreadLocalRequest(new JdkRequest(app.makeRelativePath(rootURI), "", t));

    if (requiresAuth && !app.checkLoggedIn((SPRequest) app.getRequest()))
      return;

    String uri = t.getRequestURI().getPath();

    if (uri.endsWith("/"))
      uri += "index.html";
    String path = uri.replaceFirst("^" + rootURI, "");

    int status = 200;
    InputStream in = null;

    try {
      in = openPath(path);
      String contentType = mime.getContentType(path);
      if (contentType != null)
        t.getResponseHeaders().add("Content-Type", contentType);
    }
    catch (FileNotFoundException e) {
      status = 404;
      Log.error("NOT FOUND: " + uri);
    }
    catch (Exception ex) {
      status = 500;
      Log.error(ex, ex);
    }

    t.sendResponseHeaders(status, 0);
    if (in != null)
      IO.pipe(in, t.getResponseBody(), true);
    else {
      t.getResponseBody().write("Not found".getBytes());
      t.getResponseBody().close();
    }
  }

  protected abstract InputStream openPath(String p)
      throws FileNotFoundException;
}
