package flak.plugin.resource;

import java.io.File;

import flak.App;
import flak.ContentTypeProvider;
import flak.DefaultContentTypeProvider;
import flak.ResourceOptions;

/**
 * Serves static resources, now through {@link App#serveDir} and
 * {@link App#serveClasspath}, which it only calls.
 *
 * @author pcdv
 * @deprecated kept so that code written for flak 2.x runs and compiles
 * unchanged. Replace
 * <pre>
 * new FlakResourceImpl(app).servePath("/ui", "/webapp", loader, restricted);
 * new FlakResourceImpl(app).serveDir("/files", dir);
 * </pre>
 * with
 * <pre>
 * app.serveClasspath("/ui", "/webapp", new ResourceOptions().classLoader(loader));
 * app.serveDir("/files", dir);
 * </pre>
 * adding {@link ResourceOptions#restricted()} when restricted is true, then
 * drop the dependency on flak-resource.
 */
@Deprecated
public class FlakResourceImpl implements FlakResource {

  private final App app;

  private ContentTypeProvider mime = new DefaultContentTypeProvider();

  public FlakResourceImpl(App app) {
    this.app = app;
  }

  /**
   * @deprecated see {@link ResourceOptions#contentTypes(ContentTypeProvider)}
   */
  @Deprecated
  public void setContentTypeProvider(ContentTypeProvider mime) {
    this.mime = mime;
  }

  /**
   * @deprecated see {@link App#serveDir(String, File)}
   */
  @Deprecated
  public FlakResource serveDir(String rootURI, File dir) {
    return serveDir(rootURI, dir, false);
  }

  /**
   * @deprecated see {@link App#serveDir(String, File, ResourceOptions)}
   */
  @Deprecated
  public FlakResource serveDir(String rootURI, File dir, boolean restricted) {
    app.serveDir(rootURI, dir, options(null, restricted));
    return this;
  }

  /**
   * @deprecated see {@link App#serveClasspath(String, String)} and
   * {@link App#serveDir(String, File)}
   */
  @Deprecated
  public FlakResource servePath(String rootURI, String path) {
    return servePath(rootURI, path, null, false);
  }

  /**
   * Serves a directory of the file system if resourcesPath is one, and
   * resources of the classpath otherwise, as in flak 2.x.
   *
   * @deprecated see {@link App#serveClasspath(String, String, ResourceOptions)}
   * and {@link App#serveDir(String, File, ResourceOptions)}
   */
  @Deprecated
  public FlakResource servePath(String rootURI,
                                String resourcesPath,
                                ClassLoader loader,
                                boolean restricted) {
    File file = new File(resourcesPath);
    if (file.isDirectory())
      app.serveDir(rootURI, file, options(null, restricted));
    else
      app.serveClasspath(rootURI, resourcesPath, options(loader, restricted));
    return this;
  }

  private ResourceOptions options(ClassLoader loader, boolean restricted) {
    ResourceOptions options = new ResourceOptions().classLoader(loader)
                                                   .contentTypes(mime);
    return restricted ? options.restricted() : options;
  }
}
