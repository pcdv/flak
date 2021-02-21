package flak.spi.parsers;

import flak.Request;

/**
 * @author pcdv
 */
public class QueryParser implements flak.InputParser {
  @Override
  public Object parse(Request req, Class type) {
    return req.getQuery();
  }
}
