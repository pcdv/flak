package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.OutputFormatter;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.SPPlugin;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author pcdv
 */
public class JacksonPlugin implements SPPlugin {

  /**
   * By default, create a new mapper on each call to avoid contention
   * with concurrent requests.
   */
  private MapperProvider mapper = id -> new ObjectMapper();

  private final AbstractApp app;

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

  public void setObjectMapperProvider(MapperProvider mapper) {
    this.mapper = Objects.requireNonNull(mapper);
  }

  void init() {
    app.addInputParser("JSON", new JsonInputParser<>(mapper));

    // add an indirection so we can change the mapper provider after the output
    // formatter has been set
    app.addOutputFormatter("JSON",
                           new JsonOutputFormatter<>(id -> mapper.getMapper(id)));

    app.addPlugin(this);
  }
}
