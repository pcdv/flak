package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.Form;
import flak.InputParser;
import flak.RouteParameter;
import flak.spi.AbstractMethodHandler;
import flak.spi.SPPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;

/**
 * @author pcdv
 */
public class JacksonPlugin implements SPPlugin {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Map<String, JsonOutputFormatter<?>> formatters = new Hashtable<>();
  private final Map<Class<?>, InputParser<?>> parsers = new Hashtable<>();

  private MapperProvider mapperProvider = new DefaultMapperProvider(OBJECT_MAPPER);

  JacksonPlugin() {
  }

  /**
   * Convenience method for getting the Jackson plugin from an app.
   */
  public static JacksonPlugin get(App app) {
    return app.getPlugin(JacksonPlugin.class);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void preInit(AbstractMethodHandler handler) {
    Method m = handler.getJavaMethod();

    JSON json = m.getAnnotation(JSON.class);

    if (json != null) {
      String id = json.value();

      // convert response to JSON automatically
      JsonOutputFormatter<?> fmt = formatters.computeIfAbsent(id,
                                                              i -> new JsonOutputFormatter<>(
                                                                mapperProvider.getMapper(
                                                                  id).writer()));
      handler.setOutputFormatter(fmt);
    }

    Parameter body = handler.getParameters()
                            .stream()
                            .filter(p -> p.kind() == RouteParameter.Kind.BODY
                                         && p.type() != Form.class)
                            .map(RouteParameter::javaParameter)
                            .findFirst()
                            .orElse(null);

    JSON param = body != null && body.isAnnotationPresent(JSON.class)
      ? body.getAnnotation(JSON.class)
      : json;
    if (param != null) {
      // also convert the body from JSON
      Class<?> inputClass = param.inputClass();
      if (inputClass == Object.class && body != null)
        inputClass = body.getType();

      handler.setInputParser(parsers.computeIfAbsent(inputClass,
                                                     c -> {
                                                       if (c == Object.class)
                                                         return new JsonInputMapper(
                                                           mapperProvider);
                                                       else
                                                         return new JsonInputReader(
                                                           mapperProvider.getMapper(param.value()).readerFor(
                                                             c));
                                                     }));
    }
  }

  /**
   * @deprecated  it should no longer be necessary to call this method now that ObjectReader
   * and ObjectWriter are used to process JSON data. Instead, you can use {@link #registerMapper(String, ObjectMapper)}
   */
  @Deprecated
  public void setObjectMapperProvider(MapperProvider mapper) {
    this.mapperProvider = Objects.requireNonNull(mapper);
  }

  /**
   * Registers a mapper with given id. For example, mapper "FOO" will be used
   * with route handlers decorated with <code>@JSON("FOO")</code>.
   *
   * You can use the "default" ID to override the default mapper.
   */
  public void registerMapper(String id, ObjectMapper mapper) {
    if (mapperProvider instanceof DefaultMapperProvider) {
      ((DefaultMapperProvider) mapperProvider).registerMapper(id, mapper);
    }
    else throw new IllegalStateException(
      "Cannot register mapper: a custom MapperProvider has been set");
  }
}
