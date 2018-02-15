package flak.plugin.resource;

import java.io.FileNotFoundException;
import java.io.InputStream;

import flak.ContentTypeProvider;
import flak.util.Log;

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

  @Override
  protected InputStream openPath(String p) throws FileNotFoundException {
    p = localPath + p;

    if (!p.startsWith("/"))
      p = "/" + p;

    // getResourceAsStream() fails when reading from a jar if a / is doubled!
    p = p.replace("//", "/");

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

    return in;
  }
}
