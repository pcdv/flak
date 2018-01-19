package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.Response;
import flak.OutputFormatter;

/**
 * @author pcdv
 */
public class JsonOutputConverter<T> implements OutputFormatter<T> {
  @Override
  public void convert(T data, Response resp) throws Exception {
    resp.setStatus(200);
    new ObjectMapper().writeValue(resp.getOutputStream(), data);
  }
}
