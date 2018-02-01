package flak.backend.jdk;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import flak.Form;

/**
 * @author pcdv
 */
public class FormImpl implements Form {
  private String data;

  public FormImpl(String data) {
    this.data = data;
  }

  @Override
  public String get(String name) {
    String s = data.replaceAll(".*(?:^|&)" + name + "=([^&]*).*", "$1");
    try {
      return URLDecoder.decode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      return e.toString();
    }
  }
}
