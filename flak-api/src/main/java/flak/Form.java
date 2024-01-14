package flak;

import java.util.Collection;
import java.util.Map;

/**
 * Allows to extract data from a request, typically a POST with content type
 * "application/x-www-form-urlencoded".
 *
 * @author pcdv
 */
public interface Form {
  /**
   * Returns parameter with specified name, or null if absent.
   */
  String get(String name);

  /**
   * Returns parameter with specified name.
   *
   * @param def default value if absent
   */
  String get(String name, String def);

  /**
   * Returns parameter with specified name, parsed as a boolean.
   *
   * @param def default value if absent
   */
  default int getInt(String name, int def) {
    String s = get(name);
    return s == null ? def : Integer.parseInt(s);
  }

  /**
   * Returns parameter with specified name, parsed as a boolean.
   *
   * @param def default value if absent
   */
  default boolean getBool(String name, boolean def) {
    String s = get(name);
    return s == null ? def : Boolean.parseBoolean(s);
  }

  /**
   * Returns all parameters as a sequence of key-value pairs.
   */
  Collection<Map.Entry<String, String>> parameters();

  /**
   * Returns all occurrences of specified parameter as an array.
   */
  String[] getArray(String name);
}
