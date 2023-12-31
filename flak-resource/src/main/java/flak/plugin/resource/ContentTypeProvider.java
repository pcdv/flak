package flak.plugin.resource;

/**
 * Provides the mapping between a resource's path and the content type to
 * associate it with. It is used by resource handlers and the typical
 * implementation is to look at the file extension in path.
 */
public interface ContentTypeProvider {

  /**
   * Returns the content type to set in response headers when serving resource
   * at specified path.
   */
  String getContentType(String path);

  /**
   * Used when serving resources, to help determine if a document should be
   * compressed.
   */
  default boolean shouldCompress(String contentType) {
    return contentType.contains("text") || contentType.contains("/json");
  }
}
