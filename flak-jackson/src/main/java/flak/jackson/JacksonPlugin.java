package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.InputParser;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.SPPlugin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;

/**
 * @author pcdv
 */
public class JacksonPlugin implements SPPlugin {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AbstractApp app;

  private final Map<String, JsonOutputFormatter<?>> formatters = new Hashtable<>();
  private final Map<Class<?>, InputParser<?>> parsers = new Hashtable<>();

  private MapperProvider mapperProvider = new DefaultMapperProvider(OBJECT_MAPPER);

  JacksonPlugin(App app) {
    this.app = (AbstractApp) app;
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
      if (m.getReturnType() != void.class) {
        JsonOutputFormatter<?> fmt = formatters.computeIfAbsent(id,
                                                                i -> new JsonOutputFormatter<>(
                                                                  mapperProvider.getMapper(
                                                                    id).writer()));
        handler.setOutputFormatter(fmt);
      }
    }

    JSON param = paramAnnotation(m);
    if (param != null) {
      // also convert extra args from JSON
      Class<?> inputClass = resolveInputType(param, m);

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

  private JSON paramAnnotation(Method m) {
    Annotation[][] pa = m.getParameterAnnotations();
    if (pa.length > 0) {
      Annotation[] last = pa[pa.length - 1];
      for (Annotation a : last) {
        if (a instanceof JSON)
          return (JSON) a;
      }
    }
    return m.getAnnotation(JSON.class);
  }

  /**
   * Inspects method parameter types to guess which class should be parsed from JSON.
   */
  private Class<?> resolveInputType(JSON json, Method m) {
    Class<?> inputClass = json.inputClass();

    if (inputClass == Object.class) {
      Class<?>[] types = m.getParameterTypes();
      if (types.length > 0) {
        Class<?> type = types[types.length - 1];
        String name = type.getName();
        if (!name.startsWith("flak.") && !name.startsWith("java.lang.")) {
          return type;
        }
      }
    }

    return inputClass;
  }

  /**
   * @deprecated  it should no longer be necessary to call this method now that ObjectReader
   * and ObjectWriter are used to process JSON data. Instead, you can use {@link #registerMapper(String, ObjectMapper)}
   */
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

  void init() {
    app.addPlugin(this);
  }
}
