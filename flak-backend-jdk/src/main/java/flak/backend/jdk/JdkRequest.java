package flak.backend.jdk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import flak.Request;
import flak.Response;
import flak.util.IO;

public class JdkRequest implements Request, Response {

  private final HttpExchange exchange;

  private final int qsMark;

  private final String uri;

  private String form;

  public JdkRequest(HttpExchange r) {
    this.exchange = r;
    this.uri = r.getRequestURI().toString();
    this.qsMark = uri.indexOf('?');
  }

  public String getRequestURI() {
    return qsMark >= 0 ? uri.substring(0, qsMark) : uri;
  }

  public String getQueryString() {
    return qsMark >= 0 ? uri.substring(qsMark + 1) : null;
  }

  public HttpExchange getExchange() {
    return exchange;
  }

  public String getMethod() {
    return exchange.getRequestMethod();
  }

  public String getArg(String name, String def) {
    if (qsMark == -1)
      return def;
    return parseArg(name, def, getQueryString());
  }

  private String parseArg(String name, String def, String encoded) {
    String[] tok = encoded.split("&");
    for (String s : tok) {
      if (s.startsWith(name)) {
        if (s.length() > name.length() && s.charAt(name.length()) == '=')
          return s.substring(name.length() + 1);
      }
    }
    return def;
  }

  @Override
  public String getForm(String field) {
    try {
      if (form == null)
        form = new String(IO.readFully(getInputStream()));
      return parseArg(field, null, form);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<String> getArgs(String name) {
    // TODO
    return null;
  }

  public InputStream getInputStream() {
    return exchange.getRequestBody();
  }

  // /////////// Response methods

  public void addHeader(String header, String value) {
    exchange.getResponseHeaders().add(header, value);
  }

  public void setStatus(int status) {
    try {
      exchange.sendResponseHeaders(status, 0);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public OutputStream getOutputStream() {
    return exchange.getResponseBody();
  }

}
