package flak.plugin.resource;

import java.io.File;

/**
 * @author pcdv
 */
public interface FlakResource {

  /**
   * Serves the contents of a given path (which may be a directory on the file
   * system or nested in a jar from the classpath) from a given root URI.
   * <p>
   * WARNING: if rootURI == "/" beware of conflicts with other handlers with
   * root URLs like "/foo": they will conflict with the resource handler. Prefer
   * using separate URI paths, like "/api", "/static" etc.
   *
   * @param rootURI the path at which resources will be accessible from clients
   * @param resourcesPath the actual path of resources on server
   * @param loader the class loader that will be used to find resources
   * (optional but may be required if resources are not accessible from default
   * class loader)
   * @param restricted indicates whether users must be logged in to access
   * resources
   * @return this
   */
  FlakResource servePath(String rootURI,
                         String resourcesPath,
                         ClassLoader loader,
                         boolean restricted);

  /**
   * Same as {@link #servePath(String, String, ClassLoader, boolean)} with
   * default class loader and no logged in restriction.
   */
  FlakResource servePath(String rootURI, String path);

  /**
   * Same as {@link #servePath(String, String, ClassLoader, boolean)} except
   * that only a local directory is supported.
   */
  FlakResource serveDir(String rootURI, File dir);

}
