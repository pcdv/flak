package flak.jackson;

import flak.App;

/**
 * @author pcdv
 */
public class JacksonPlugin {
  public static void install(App app) {
    app.addInputParser("JSON", new JsonInputParser());
    app.addOutputFormatter("JSON", new JsonOutputFormatter<>());
  }
}
