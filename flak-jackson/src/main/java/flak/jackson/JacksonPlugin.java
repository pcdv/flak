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

  private MapperProvider mapperProvider = id -> {
    if (id == null || id.isEmpty())
      return OBJECT_MAPPER;
    else
      throw new IllegalStateException("Unknown JSON mapper ID: " + id + ". Call JacksonPlugin.setObjectMapperProvider() earlier");
  };

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
          i -> new JsonOutputFormatter<>(mapperProvider.getMapper(id).writer()));
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
            return new JsonInputMapper(mapperProvider);
          else
            return new JsonInputReader(mapperProvider.getMapper(param.value()).readerFor(c));
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
        if (! name.startsWith("flak.") && ! name.startsWith("java.lang.")) {
          return type;
        }
      }
    }

    return inputClass;
  }

  public void setObjectMapperProvider(MapperProvider mapper) {
    this.mapperProvider = Objects.requireNonNull(mapper);
  }

  void init() {
    app.addPlugin(this);
  }
}
