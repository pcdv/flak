package flak.spi.resource;

import flak.ContentTypeProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import flak.spi.SPResponse;
import flak.spi.util.Log;

/**
 * Serves files nested in a jar from classpath.
 *
 * @author pcdv
 */
public class ResourceHandler extends AbstractResourceHandler {

  private final String localPath;

  private final ClassLoader loader;

  public ResourceHandler(ContentTypeProvider mime,
                         String rootURI,
                         String localPath,
                         ClassLoader loader,
                         boolean requiresAuth) {
    super(mime, rootURI, requiresAuth);
    if (!localPath.endsWith("/"))
      localPath += "/";
    this.localPath = localPath;
    this.loader = loader;
  }

  private String fullPath(String p) {
    p = localPath + p;

    if (!p.startsWith("/"))
      p = "/" + p;

    // getResourceAsStream() fails when reading from a jar if a / is doubled!
    return p.replace("//", "/");
  }

  /**
   * The time of the file in the classpath directory, or of the entry in the
   * jar: it only changes with the build, unlike the start time of the app.
   */
  @Override
  protected long lastModified(String p) {
    p = fullPath(p);
    URL url = loader == null ? getClass().getResource(p) : loader.getResource(p.substring(1));
    if (url == null)
      return 0;
    try {
      URLConnection con = url.openConnection();
      long time = con.getLastModified();
      // a file: connection opens the file to read its headers
      con.getInputStream().close();
      return time;
    }
    catch (IOException e) {
      return 0;
    }
  }

  @Override
  protected InputStream openPath(String p, SPResponse resp) throws FileNotFoundException {
    p = fullPath(p);

    if (Log.DEBUG)
      Log.debug("Trying to open " + p + " with loader " + loader);

    InputStream in;

    // behaviour is not the same via class or class loader!!!
    if (loader == null)
      in = getClass().getResourceAsStream(p);
    else {
      in = loader.getResourceAsStream(p.substring(1));
    }

    if (in == null)
      throw new FileNotFoundException(p);

    // no way of knowing the size of resource, so we always allow compression
    resp.setCompressionAllowed(true);

    return in;
  }
}
