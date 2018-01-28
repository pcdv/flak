package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public class RequestExtractor extends ArgExtractor {
  public RequestExtractor(int i) {super(i);}

  @Override
  public Object extract(JdkRequest request) {
    return request;
  }
}
