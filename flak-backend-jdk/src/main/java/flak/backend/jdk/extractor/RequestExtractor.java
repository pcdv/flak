package flak.backend.jdk.extractor;

import flak.Request;
import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class RequestExtractor extends ArgExtractor<Request> {
  public RequestExtractor(int index) {
    super(index);
  }

  @Override
  public Request extract(SPRequest request) {
    return request;
  }
}
