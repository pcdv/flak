package flak.spi.parsers;

import flak.Request;

/**
 * @author pcdv
 */
public class FormParser implements flak.InputParser {
  @Override
  public Object parse(Request req, Class type) {
    return req.getForm();
  }
}
