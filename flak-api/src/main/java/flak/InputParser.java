package flak;

/**
 * @author pcdv
 */
public interface InputParser {
  Object parse(Request req, Class type) throws Exception;
}
