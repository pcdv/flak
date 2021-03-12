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
  String get(String name);

  String get(String name, String def);

  default int getInt(String name, int def) {
    String s = get(name);
    return s == null ? def:Integer.parseInt(s);
  }

  default boolean getBool(String name, boolean def) {
    String s = get(name);
    return s == null ? def:Boolean.parseBoolean(s);
  }

  Collection<Map.Entry<String, String>> parameters();
}
