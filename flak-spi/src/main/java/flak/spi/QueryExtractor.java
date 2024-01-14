package flak.spi;

import flak.annotations.QueryParam;

import java.lang.reflect.Parameter;

class QueryExtractor {
  static ArgExtractor<?> from(QueryParam annotation, Parameter param, int index) {
    Class<?> type = param.getType();
    switch (type.getSimpleName()) {
      case "String":
        return new StringQueryExtractor(index, annotation.value());

      case "String[]":
        return new StringArrayQueryExtractor(index, annotation.value());

      case "int":
      case "Integer":
        return new IntQueryExtractor(index, annotation.value());

      default:
        throw new IllegalArgumentException("Unsupported type for a query parameter: " + param);
    }
  }

  private static class StringQueryExtractor extends ArgExtractor<String> {
    private final String name;

    public StringQueryExtractor(int index, String name) {
      super(index);
      this.name = name;
    }

    @Override
    public String extract(SPRequest request) {
      return request.getQuery().get(name);
    }
  }

  private static class StringArrayQueryExtractor extends ArgExtractor<String[]> {
    private final String name;

    public StringArrayQueryExtractor(int index, String name) {
      super(index);
      this.name = name;
    }

    @Override
    public String[] extract(SPRequest request) {
      return request.getQuery().getArray(name);
    }
  }

  private static class IntQueryExtractor extends ArgExtractor<Integer> {
    private final String name;

    public IntQueryExtractor(int index, String name) {
      super(index);
      this.name = name;
    }

    @Override
    public Integer extract(SPRequest request) {
      return request.getQuery().getInt(name, -1);
    }
  }
}
