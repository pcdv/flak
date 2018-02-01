package flak.backend.jdk;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import flak.Form;
import flak.Query;

/**
 * @author pcdv
 */
public class FormImpl implements Form, Query {
  private String data;

  public FormImpl(String data) {
    this.data = data;
  }

  @Override
  public String get(String name) {
    if (data == null)
      return null;
    String s = data.replaceAll(".*(?:^|&)" + name + "=([^&]*).*", "$1");
    try {
      return URLDecoder.decode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      return e.toString();
    }
  }

  @Override
  public String get(String name, String def) {
    String res = get(name);
    return res == null || res.equals(data)? def : res;
  }
}
