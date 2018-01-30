package flak.backend.jdk.extractor;

import flak.Request;
import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public class RequestExtractor extends ArgExtractor<Request> {
  public RequestExtractor(int index) {
    super(index);
  }

  @Override
  public Request extract(JdkRequest request) {
    return request;
  }
}
