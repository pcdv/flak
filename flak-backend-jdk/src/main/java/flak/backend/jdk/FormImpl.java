package flak.backend.jdk;

import flak.Form;
import flak.Query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pcdv
 */
public class FormImpl implements Form, Query {

  private final Map<String, String> data = new LinkedHashMap<>();

  public FormImpl(String data) {
    try {
      if (data != null)
        for (String tok : data.split("&")) {
          int pos = tok.indexOf('=');
          if (pos != -1) {
            this.data.put(tok.substring(0, pos),
                          URLDecoder.decode(tok.substring(pos + 1), "UTF-8"));
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
    return data.entrySet();
  }
}
