package flak.spi;

import flak.InputParser;
import flak.OutputFormatter;
import flak.RouteParameter;

import java.lang.reflect.Method;
import java.util.List;

/**
 * What a route handler is made of, as read from the annotations of its
 * method, before the backend builds the handler. Flak's own annotations are
 * read by {@link FlakAnnotations}; the handler and the backends do not look
 * at them.
 *
 * @param route           the route, relative to the app, e.g. "/items/:id"
 * @param httpMethod      e.g. "GET"
 * @param javaMethod      the method invoked to serve requests
 * @param parameters      what each parameter of the method is bound to
 * @param outputFormatter converts what the method returns, null for the
 *                        basic types (String, byte[], InputStream...)
 * @param inputParser     reads the body into the parameters of kind BODY,
 *                        null if none is declared
 * @param compress        whether the response may be compressed
 * @param maxBodySize     the limit of the request body, null to leave it to
 *                        the app
 */
public record HandlerSpec(String route,
                          String httpMethod,
                          Method javaMethod,
                          List<RouteParameter> parameters,
                          OutputFormatter<?> outputFormatter,
                          InputParser<?> inputParser,
                          boolean compress,
                          Long maxBodySize) {

  public HandlerSpec {
    parameters = List.copyOf(parameters);
  }
}
