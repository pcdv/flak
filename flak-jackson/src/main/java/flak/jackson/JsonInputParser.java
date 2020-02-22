package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.InputParser;
import flak.Request;

/**
 * @author pcdv
 */
public class JsonInputParser implements InputParser {

  private final MapperProvider mapper;

  public JsonInputParser() {
    this(new DefaultMapperProvider(new ObjectMapper()));
  }

  public JsonInputParser(MapperProvider mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object parse(Request req, Class type) throws Exception {
    return mapper.getMapper(null).readValue(req.getInputStream(), type);
  }
}
