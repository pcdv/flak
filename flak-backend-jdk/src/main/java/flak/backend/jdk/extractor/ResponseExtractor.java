package flak.backend.jdk.extractor;

import flak.Response;
import flak.spi.AbstractApp;
import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class ResponseExtractor extends ArgExtractor<Response> {
  private AbstractApp app;

  public ResponseExtractor(AbstractApp app, int index) {
    super(index);
    this.app = app;
  }

  @Override
  public Response extract(SPRequest request) throws Exception {
    return app.getResponse();
  }
}
