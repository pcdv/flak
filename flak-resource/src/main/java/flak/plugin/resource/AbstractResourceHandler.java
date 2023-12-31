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

  protected abstract InputStream openPath(String p, SPResponse resp) throws IOException;
}
