package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.InputParser;
import flak.Request;

/**
 * @author pcdv
 */
public class JsonInputParser implements InputParser {

  private final ObjectMapper mapper;

  public JsonInputParser() {
    this(new ObjectMapper());
  }

  public JsonInputParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object parse(Request req, Class type) throws Exception {
    return mapper.readValue(req.getInputStream(), type);
  }
}
