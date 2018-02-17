package flak.plugin.resource;

import java.io.File;

import flak.App;
import flak.Request;
import flak.spi.AbstractApp;

/**
 * @author pcdv
 */
public class FlakResourceImpl implements FlakResource {

  private AbstractApp app;

  private ContentTypeProvider mime = new DefaultContentTypeProvider();

  public FlakResourceImpl(App app) {
    this.app = (AbstractApp) app;
  }

  public void setContentTypeProvider(ContentTypeProvider mime) {
    this.mime = mime;
  }

  /**
   * WARNING: if rootURI == "/" beware of conflicts with other handlers
   * with root URLs like "/foo": they will conflict with the resource handler.
   */
  public FlakResource serveDir(String rootURI, File dir) {
    return serveDir(rootURI, dir, false);
  }

  public FlakResource serveDir(String rootURI, File dir, boolean restricted) {
    return servePath(rootURI, dir.getAbsolutePath(), null, restricted);
  }

  /**
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler.
   */
  public FlakResource servePath(String rootURI, String path) {
    return servePath(rootURI, path, null, false);
  }

  /**
   * Serves the contents of a given path (which may be a directory on the file
   * system or nested in a jar from the classpath) from a given root URI.
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler.
   *
   * @return this
   */
  public FlakResource servePath(String rootURI,
                                String resourcesPath,
                                ClassLoader loader,
                                boolean requiresAuth) {
    File file = new File(resourcesPath);
    AbstractResourceHandler h;
    if (file.exists() && file.isDirectory())
      h = new FileHandler(mime, rootURI, file, requiresAuth);
    else
      h = new ResourceHandler(mime,
                              rootURI,
                              resourcesPath,
                              loader,
                              requiresAuth);

    try {
      app.addHandler0(rootURI + "/*splat",
                      h.getClass().getMethod("doGet", Request.class),
                      h);

    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }

    return this;
  }

}
