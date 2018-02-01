package flak;

/**
 * Allows to extract data from a request, typically a POST with content type
 * "application/x-www-form-urlencoded".
 *
 * @author pcdv
 */
public interface Form {
  String get(String name);
}
