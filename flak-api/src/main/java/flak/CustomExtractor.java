package flak;

/**
 * Builds the value of a route handler argument from the request, which allows
 * handlers to accept argument types that Flak knows nothing about.
 * <p>
 * Register an extractor with
 * {@link App#addCustomExtractor(Class, CustomExtractor)}: from then on, every
 * route handler argument of the associated type is obtained by calling
 * {@link #extract(Request)}.
 *
 * @author pcdv
 */
@FunctionalInterface
public interface CustomExtractor<T> {

  /**
   * Extracts the value of the argument from specified request.
   *
   * @param request the request being served
   * @return the value to pass to the route handler
   * @throws Exception if the argument cannot be extracted
   */
  T extract(Request request) throws Exception;
}
