package flak;

/**
 * Converts the return value of a route handler into another format (e.g. Object to JSON).
 * This is currently done by writing converted data as bytes into the response's
 * output stream.
 */
public interface OutputFormatter<T> {
  void convert(T data, Response resp) throws Exception;
}
