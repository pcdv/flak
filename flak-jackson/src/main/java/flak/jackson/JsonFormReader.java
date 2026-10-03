package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.Form;
import flak.Request;
import flak.annotations.FormParams;

/**
 * Builds the arguments annotated with {@link FormParams}, from the fields of
 * the form posted in the body, as {@link JsonQueryReader} does from the query
 * string.
 *
 * @since 3.2.0
 */
public class JsonFormReader extends JsonQueryReader {

  /**
   * @param mapper see {@link JsonQueryReader#JsonQueryReader(ObjectMapper)}
   */
  public JsonFormReader(ObjectMapper mapper) {
    super(mapper);
  }

  @Override
  protected Form source(Request req) {
    return req.getForm();
  }

  @Override
  protected String parameter() {
    return "form field";
  }

  @Override
  protected String origin() {
    return "form";
  }
}
