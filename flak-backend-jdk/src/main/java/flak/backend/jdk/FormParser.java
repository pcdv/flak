package flak.backend.jdk;

import flak.Request;
import flak.util.IO;

/**
 * @author pcdv
 */
public class FormParser implements flak.InputParser {
  @Override
  public Object parse(Request req, Class type) throws Exception {
    return new FormImpl(new String(IO.readFully(req.getInputStream())));
  }
}
