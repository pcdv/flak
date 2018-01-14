package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.Response;
import flak.ResponseConverter;

/**
 * @author pcdv
 */
public class JsonOutputConverter<T> implements ResponseConverter<T> {
  @Override
  public void convert(T data, Response resp) throws Exception {
    resp.setStatus(200);
    new ObjectMapper().writeValue(resp.getOutputStream(), data);
  }
}
