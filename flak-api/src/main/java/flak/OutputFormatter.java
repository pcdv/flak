package flak;

/**
 * Converts the return value of a route handler into another format (e.g. Object to JSON).
 */
public interface OutputFormatter<T> {
  void convert(T data, Response resp) throws Exception;
}
