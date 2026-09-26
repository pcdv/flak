package flak.spi;

import flak.HttpException;
import flak.annotations.QueryParam;

import java.lang.reflect.Parameter;
import java.util.function.Function;

/**
 * Extracts the arguments annotated with {@link QueryParam}.
 */
class QueryExtractor {
  static ArgExtractor<?> from(QueryParam annotation, Parameter param, int index) {
    Class<?> type = param.getType();
    String name = annotation.value();
    String def = QueryParam.NO_DEFAULT.equals(annotation.defaultValue())
      ? null
      : annotation.defaultValue();

    if (type == String[].class)
      return new ArrayQueryExtractor(index, name, def);

    Function<String, ?> converter = converter(type);
    if (converter == null)
      throw new IllegalArgumentException("Unsupported type for a query parameter: " + param);

    Object fallback;
    if (def != null) {
      try {
        fallback = converter.apply(def);
      }
      catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid default value for query parameter "
                                           + name + ": " + def, e);
      }
    }
    else
      fallback = missingValue(type);

    return new ValueQueryExtractor(index, name, converter, fallback, type != String.class);
  }

  /**
   * Returns what converts the value found in the query string to specified
   * type, throwing IllegalArgumentException if it cannot, or null if the type
   * is not supported.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Function<String, ?> converter(Class<?> type) {
    if (type == String.class)
      return s -> s;
    if (type == int.class || type == Integer.class)
      return Integer::valueOf;
    if (type == long.class || type == Long.class)
      return Long::valueOf;
    if (type == double.class || type == Double.class)
      return Double::valueOf;
    if (type == boolean.class || type == Boolean.class)
      return QueryExtractor::parseBoolean;
    if (type.isEnum())
      return s -> Enum.valueOf((Class<? extends Enum>) type, s);
    return null;
  }

  /**
   * Unlike Boolean.parseBoolean(), which takes anything but "true" for false,
   * rejects what is neither.
   */
  private static Boolean parseBoolean(String s) {
    if (s.equalsIgnoreCase("true"))
      return true;
    if (s.equalsIgnoreCase("false"))
      return false;
    throw new IllegalArgumentException("Not a boolean: " + s);
  }

  /**
   * The value of an absent parameter with no default: a primitive cannot be
   * null, so it gets -1 (or false), which is what an int always got.
   */
  private static Object missingValue(Class<?> type) {
    if (type == boolean.class)
      return false;
    if (type == int.class)
      return -1;
    if (type == long.class)
      return -1L;
    if (type == double.class)
      return -1.0;
    return null;
  }

  private static class ValueQueryExtractor extends ArgExtractor<Object> {
    private final String name;
    private final Function<String, ?> converter;
    private final Object fallback;
    /**
     * Whether an empty value counts as absent, e.g. "num=" for a number: it
     * does for everything but a String, for which it is a value like another.
     */
    private final boolean emptyIsAbsent;

    ValueQueryExtractor(int index,
                        String name,
                        Function<String, ?> converter,
                        Object fallback,
                        boolean emptyIsAbsent) {
      super(index);
      this.name = name;
      this.converter = converter;
      this.fallback = fallback;
      this.emptyIsAbsent = emptyIsAbsent;
    }

    @Override
    public Object extract(SPRequest request) {
      String s = request.getQuery().get(name);
      if (s == null || (emptyIsAbsent && s.isEmpty()))
        return fallback;
      try {
        return converter.apply(s);
      }
      catch (IllegalArgumentException e) {
        throw new HttpException(400, "Invalid value for query parameter " + name + ": " + s);
      }
    }
  }

  private static class ArrayQueryExtractor extends ArgExtractor<String[]> {
    private final String name;
    private final String def;

    ArrayQueryExtractor(int index, String name, String def) {
      super(index);
      this.name = name;
      this.def = def;
    }

    @Override
    public String[] extract(SPRequest request) {
      String[] values = request.getQuery().getArray(name);
      return values.length == 0 && def != null ? new String[]{def} : values;
    }
  }
}
