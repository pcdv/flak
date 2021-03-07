package flak.jackson;

import flak.InputParser;
import flak.Request;

/**
 * An InputParser using an ObjectMapper. For performance reasons, it is recommended to
 * use an ObjectReader.
 *
 * @see JsonInputReader
 */
public class JsonInputMapper<T> implements InputParser<T> {

  private final String mapperId;
  private final MapperProvider mapperProvider;

  public JsonInputMapper(MapperProvider mapperProvider) {
    this("", mapperProvider);
  }

  public JsonInputMapper(String mapperId, MapperProvider mapperProvider) {
    this.mapperId = mapperId;
    this.mapperProvider = mapperProvider;
  }

  @Override
  public T parse(Request req, Class<T> type) throws Exception {
    return mapperProvider.getMapper(mapperId).readValue(req.getInputStream(), type);
  }
}
