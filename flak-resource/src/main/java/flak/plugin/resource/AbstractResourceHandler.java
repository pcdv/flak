package flak.plugin.resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import flak.HttpException;
import flak.Request;
import flak.spi.CompressionHelper;
import flak.spi.RestrictedTarget;
import flak.spi.SPResponse;
import flak.spi.util.IO;
import flak.spi.util.Log;

/**
 * Abstract handler that Serves resources found either in the file system or
 * nested in a jar.
 *
 * @author pcdv
 */
public abstract class AbstractResourceHandler implements RestrictedTarget {

  private final String rootURI;

  private final boolean restricted;

  private final ContentTypeProvider mime;

  AbstractResourceHandler(ContentTypeProvider mime,
                          String rootURI,
                          boolean restricted) {
    this.mime = mime;
    this.rootURI = rootURI;
    this.restricted = restricted;
  }

  public boolean isRestricted() {
    return restricted;
  }

  public void doGet(Request r, String ignored) throws Exception {

    String uri = r.getPath();

    if (uri.endsWith("/"))
      uri += "index.html";
    String path = uri.replaceFirst("^" + rootURI, "");

    // the backends decode the path without normalizing it, so a ".." would
    // give access to whatever lies above the served directory
    if (climbsUp(path))
      throw new HttpException(404, "Not found");

    // a directory without its trailing slash is redirected to it, like its
    // root is (see serveRoot()): otherwise the relative links of its
    // index.html would resolve against its parent
    if (!r.getPath().endsWith("/") && isDirectory(path)) {
      redirectToDirectory(r);
      return;
    }

    InputStream in;

    OutputStream out = r.getResponse().getOutputStream();
    try {
      in = openPath(path, (SPResponse) r.getResponse());
      String contentType = mime.getContentType(path);
      if (contentType != null) {
        r.getResponse().addHeader("Content-Type", contentType);
        if (mime.shouldCompress(contentType))
          out = CompressionHelper.maybeCompress(r.getResponse());
      }
    }
    catch (FileNotFoundException e) {
      throw new HttpException(404, "Not found");
    }

    if (in != null) {
      r.getResponse().setStatus(200);
      try {
        IO.pipe(in, out, true);
      }
      catch (IOException e) {
        // avoid log pollution when the client abruptly closes the socket
        if (Log.DEBUG)
          e.printStackTrace();
      }
    }
    else {
      r.getResponse().setStatus(404);
      out.write("Not found".getBytes());
      out.close();
    }
  }

  /**
   * Serves the root of the resources, e.g. "/static" or "/static/": the
   * former is redirected to the latter, which serves the index.html of the
   * root. A route of its own, since the splat of doGet() needs at least one
   * segment.
   */
  public void serveRoot(Request r) throws Exception {
    if (r.getPath().endsWith("/"))
      doGet(r, null);
    else
      redirectToDirectory(r);
  }

  private static void redirectToDirectory(Request r) {
    String qs = r.getQueryString();
    r.getResponse().redirect(r.getPath() + "/" + (qs == null ? "" : "?" + qs));
  }

  /**
   * Tells whether specified path, relative to the root of the resources, is
   * a directory. Only known for resources in the file system.
   */
  protected boolean isDirectory(String path) {
    return false;
  }

  /**
   * Tells whether a path contains a ".." segment. Backslashes count as
   * separators too, being ones for the file system on Windows.
   */
  static boolean climbsUp(String path) {
    for (String segment : path.split("[/\\\\]")) {
      if (segment.equals(".."))
        return true;
    }
    return false;
  }

  protected abstract InputStream openPath(String p, SPResponse resp) throws IOException;
}
