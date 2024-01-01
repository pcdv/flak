package flak.backend.jdk;

import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;

class HeaderList {
  private final ArrayList<String[]> list = new ArrayList<>();

  void add(String header, String value) {
    list.add(new String[]{header, value});
  }

  void addHeadersInto(HttpExchange exchange) {
    for (String[] arr : list) {
      exchange.getResponseHeaders().add(arr[0], arr[1]);
    }
  }

  public boolean has(String name) {
    return list.stream().anyMatch(arr -> name.equals(arr[0]));
  }
}
