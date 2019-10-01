package flak.jackson;

import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.OutputFormatter;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.FlakPlugin;

/**
 * @author pcdv
 */
public class JacksonPlugin implements FlakPlugin {
  private ObjectMapper mapper;

  private AbstractApp app;

  public JacksonPlugin(App app) {
    this.app = (AbstractApp) app;
  }

  @Override
  public void preInit(AbstractMethodHandler handler) {
    Method m = handler.getJavaMethod();

    JSON json = m.getAnnotation(JSON.class);
    if (json != null) {
      OutputFormatter<?> fmt = app.getOutputFormatter("JSON");
      if (fmt == null)
        throw new IllegalArgumentException("In method " + m.getName() + ": no OutputFormatter with name JSON was declared");

      // convert response to JSON automatically
      handler.setOutputFormatter(fmt);

      // also convert extra args from JSON
      handler.setInputParser(app.getInputParser("JSON"));
    }
  }

  /**
   * @deprecated This method should not be called anymore as since 1.0 the
   * Jackson plugin is automatically installed when present in classpath.
   */
  @Deprecated
  public static void install(App app) {
  }

  public void setObjectMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  void init() {
    if (mapper == null)
      mapper = new ObjectMapper();

    app.addInputParser("JSON", new JsonInputParser(mapper));
    app.addOutputFormatter("JSON", new JsonOutputFormatter<>(mapper));

    app.addPlugin(this);
  }
}
