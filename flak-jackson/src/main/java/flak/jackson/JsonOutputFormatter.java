package flak.jackson;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.OutputFormatter;
import flak.Response;

/**
 * @author pcdv
 */
public class JsonOutputFormatter<T> implements OutputFormatter<T> {

  private final MapperProvider mapper;

  public JsonOutputFormatter() {
    this(new DefaultMapperProvider(new ObjectMapper()));
  }

  public JsonOutputFormatter(MapperProvider mapper) {
    this.mapper = mapper;
  }

  @Override
  public void convert(T data, Response resp) throws Exception {
    JSON ann = resp.getRequest().getHandler().getAnnotation(JSON.class);
    String id = ann == null ? null : ann.value();
    convert(data, resp, id);
  }

  private void convert(T data, Response resp, String id) throws IOException {
    ObjectMapper mapper = this.mapper.getMapper(id);
    resp.addHeader("Content-Type", "application/json");
    resp.setStatus(200);
    mapper.writeValue(resp.getOutputStream(), data);
  }
}
