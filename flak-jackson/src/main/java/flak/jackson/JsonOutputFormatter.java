package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.OutputFormatter;
import flak.Response;

/**
 * @author pcdv
 */
public class JsonOutputFormatter<T> implements OutputFormatter<T> {

  private final ObjectMapper mapper;

  public JsonOutputFormatter() {
    this(new ObjectMapper());
  }

  public JsonOutputFormatter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void convert(T data, Response resp) throws Exception {
    resp.setStatus(200);
    mapper.writeValue(resp.getOutputStream(), data);
  }
}
