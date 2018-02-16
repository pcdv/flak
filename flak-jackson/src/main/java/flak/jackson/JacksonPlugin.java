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

      handler.setOutputFormatter(fmt);

      if (AbstractMethodHandler.isNotBasic(m.getReturnType())) {
        handler.setInputParser(app.getInputParser("JSON"));
      }
    }

  }

  public static void install(App app) {
    new JacksonPlugin(app).install0();
  }

  public void setObjectMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  private void install0() {
    if (mapper == null)
      mapper = new ObjectMapper();

    app.addInputParser("JSON", new JsonInputParser(mapper));
    app.addOutputFormatter("JSON", new JsonOutputFormatter<>(mapper));

    app.addPlugin(this);
  }
}
