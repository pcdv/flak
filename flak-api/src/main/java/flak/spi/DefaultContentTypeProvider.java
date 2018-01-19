package flak.spi;

import flak.ContentTypeProvider;

public class DefaultContentTypeProvider implements ContentTypeProvider {

  public String getContentType(String path) {
    path = path.toLowerCase();
    if (path.endsWith(".js"))
      return "application/javascript";
    if (path.endsWith(".css"))
      return "text/css";
    if (path.endsWith(".woff"))
      return "application/x-font-woff";

    int dot = path.lastIndexOf(".");
    if (dot != -1)
      return "text/" + path.substring(dot + 1);

    return null;
  }
}
