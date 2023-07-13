package flak.backend.jdk;

import flak.Form;
import flak.Query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pcdv
 */
public class FormImpl implements Form, Query {

  private final Map<String, String> data = new LinkedHashMap<>();
  private final List<Map.Entry<String, String>> params = new ArrayList<>();

  public FormImpl(String data, boolean urlDecode) {
    try {
      if (data != null)
        for (String tok : data.split("&")) {
          int pos = tok.indexOf('=');
          if (pos != - 1) {
            String key = tok.substring(0, pos);
            String value = tok.substring(pos + 1);
            this.data.put(key, urlDecode ? URLDecoder.decode(value, "UTF-8") : value);
            this.params.add(new AbstractMap.SimpleEntry<>(key, value));
          }
        }
    }
    catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
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
}
