package flak.plugin.resource;

import java.util.HashMap;
import java.util.Map;

public class DefaultContentTypeProvider implements ContentTypeProvider {

  private static Map<String, String> map = new HashMap<>();

  // generated from https://developer.mozilla.org/fr/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
  static {
    map.put("aac", "audio/aac");
    map.put("abw", "application/x-abiword");
    map.put("arc", "application/octet-stream");
    map.put("avi", "video/x-msvideo");
    map.put("azw", "application/vnd.amazon.ebook");
    map.put("bin", "application/octet-stream");
    map.put("bz", "application/x-bzip");
    map.put("bz2", "application/x-bzip2");
    map.put("csh", "application/x-csh");
    map.put("css", "text/css");
    map.put("csv", "text/csv");
    map.put("doc", "application/msword");
    map.put("docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    map.put("eot", "application/vnd.ms-fontobject");
    map.put("epub", "application/epub+zip");
    map.put("gif", "image/gif");
    map.put("htm", "text/html");
    map.put("html", "text/html");
    map.put("ico", "image/x-icon");
    map.put("ics", "text/calendar");
    map.put("jar", "application/java-archive");
    map.put("jpeg", "image/jpeg");
    map.put("jpg", "image/jpeg");
    map.put("js", "application/javascript");
    map.put("json", "application/json");
    map.put("mid", "audio/midi");
    map.put("midi", "audio/midi");
    map.put("mpeg", "video/mpeg");
    map.put("mpkg", "application/vnd.apple.installer+xml");
    map.put("odp", "application/vnd.oasis.opendocument.presentation");
    map.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
    map.put("odt", "application/vnd.oasis.opendocument.text");
    map.put("oga", "audio/ogg");
    map.put("ogv", "video/ogg");
    map.put("ogx", "application/ogg");
    map.put("otf", "font/otf");
    map.put("png", "image/png");
    map.put("pdf", "application/pdf");
    map.put("ppt", "application/vnd.ms-powerpoint");
    map.put("pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    map.put("rar", "application/x-rar-compressed");
    map.put("rtf", "application/rtf");
    map.put("sh", "application/x-sh");
    map.put("svg", "image/svg+xml");
    map.put("swf", "application/x-shockwave-flash");
    map.put("tar", "application/x-tar");
    map.put("tif", "image/tiff");
    map.put("tiff", "image/tiff");
    map.put("ts", "application/typescript");
    map.put("ttf", "font/ttf");
    map.put("vsd", "application/vnd.visio");
    map.put("wav", "audio/x-wav");
    map.put("weba", "audio/webm");
    map.put("webm", "video/webm");
    map.put("webp", "image/webp");
    map.put("woff", "font/woff");
    map.put("woff2", "font/woff2");
    map.put("xhtml", "application/xhtml+xml");
    map.put("xls", "application/vnd.ms-excel");
    map.put("xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    map.put("xml", "application/xml");
    map.put("xul", "application/vnd.mozilla.xul+xml");
    map.put("zip", "application/zip");
    map.put("3gp", "video/3gpp");
    map.put("3g2", "video/3gpp2");
    map.put("7z", "application/x-7z-compressed");
  }

  public String getContentType(String path) {
    path = path.toLowerCase();
    int dot = path.lastIndexOf(".");
    if (dot != -1) {
      String ext = path.substring(dot + 1);
      return map.get(ext);
    }

    return null;
  }
}
