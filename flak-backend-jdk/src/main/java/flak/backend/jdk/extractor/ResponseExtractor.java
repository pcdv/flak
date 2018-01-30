package flak.backend.jdk.extractor;

import flak.Response;
import flak.backend.jdk.JdkApp;
import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public class ResponseExtractor extends ArgExtractor<Response> {
  private JdkApp app;

  public ResponseExtractor(JdkApp app, int index) {
    super(index);
    this.app = app;
  }

  @Override
  public Response extract(JdkRequest request) throws Exception {
    return app.getResponse();
  }
}
