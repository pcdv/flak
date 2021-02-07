package flak;

/**
 * Converts the content of a Request into any object that can be accepted
 * by a route handler.
 *
 * @author pcdv
 */
public interface InputParser<T> {
  T parse(Request req, Class<T> type) throws Exception;
}
