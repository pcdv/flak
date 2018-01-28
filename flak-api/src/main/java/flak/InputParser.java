package flak;

/**
 * Converts the content of a Request into any object that can be accepted
 * by a route handler.
 *
 * @author pcdv
 */
public interface InputParser {
  Object parse(Request req, Class type) throws Exception;
}
