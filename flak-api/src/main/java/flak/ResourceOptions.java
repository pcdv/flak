package flak;

/**
 * How static resources are served, see {@link App#serveDir} and
 * {@link App#serveClasspath}, e.g.
 * <pre>
 * app.serveClasspath("/ui", "/webapp", new ResourceOptions().restricted());
 * </pre>
 *
 * @author pcdv
 */
public class ResourceOptions {

  private boolean restricted;

  private ClassLoader classLoader;

  private ContentTypeProvider contentTypes = new DefaultContentTypeProvider();

  private String cacheControl;

  /**
   * Restricts the resources to logged-in users, which requires flak-login:
   * serving them fails if it is not installed, rather than leaving them open.
   */
  public ResourceOptions restricted() {
    this.restricted = true;
    return this;
  }

  /**
   * The class loader to look classpath resources up with, when they are not
   * visible to that of Flak, e.g. in an application server. Ignored for a
   * directory.
   */
  public ResourceOptions classLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
    return this;
  }

  /**
   * Decides the content type of each file, and whether it is compressed.
   * Defaults to {@link DefaultContentTypeProvider}.
   */
  public ResourceOptions contentTypes(ContentTypeProvider contentTypes) {
    this.contentTypes = contentTypes;
    return this;
  }

  /**
   * The Cache-Control header of the files served, e.g.
   * "public, max-age=31536000, immutable" for files whose name holds a hash
   * of their content, as a bundler outputs. None by default.
   */
  public ResourceOptions cacheControl(String cacheControl) {
    this.cacheControl = cacheControl;
    return this;
  }

  public boolean isRestricted() {
    return restricted;
  }

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  public ContentTypeProvider getContentTypes() {
    return contentTypes;
  }

  public String getCacheControl() {
    return cacheControl;
  }
}
