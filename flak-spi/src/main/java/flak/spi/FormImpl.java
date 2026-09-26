package flak.spi;

import flak.Form;
import flak.HttpException;
import flak.Query;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses data in application/x-www-form-urlencoded format, i.e. a form sent
 * in the body of a POST, or a query string.
 *
 * @author pcdv
 */
public class FormImpl implements Form, Query {

  private final Map<String, String> data = new LinkedHashMap<>();
  private final List<Map.Entry<String, String>> params = new ArrayList<>();

  /**
   * @param data the encoded data, e.g. "a=1&b=x+y", or null for none
   * @param urlDecode whether to decode the names and values, which is always
   * the case unless data was decoded already. It must be decoded after being
   * split: decoded before, an encoded '&' or '=' in a value would split it
   */
  public FormImpl(String data, boolean urlDecode) {
    if (data != null)
      for (String tok : data.split("&")) {
        int pos = tok.indexOf('=');
        if (pos != - 1) {
          String key = tok.substring(0, pos);
          String value = tok.substring(pos + 1);
          if (urlDecode) {
            key = decode(key);
            value = decode(value);
          }
          this.data.put(key, value);
          this.params.add(new AbstractMap.SimpleEntry<>(key, value));
        }
      }
  }

  /**
   * Decodes as HTML forms encode: '+' is a space, "%2B" a '+'.
   */
  private static String decode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e) {
      // e.g. "%zz": the client's mistake, not a server error
      throw new HttpException(400, "Malformed url-encoded data: " + s);
    }
  }

  @Override
  public String get(String name) {
    return data.get(name);
  }

  @Override
  public String get(String name, String def) {
    String res = get(name);
    return res == null ? def : res;
  }

  @Override
  public Collection<Map.Entry<String, String>> parameters() {
    return params;
  }

  @Override
  public String[] getArray(String name) {
    return params.stream().filter(p -> p.getKey().equals(name)).map(Map.Entry::getValue).toArray(String[]::new);
  }
}
