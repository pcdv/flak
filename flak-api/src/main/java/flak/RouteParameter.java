package flak;

import java.lang.reflect.Parameter;

/**
 * A parameter of a route handler, and what Flak binds it to, e.g. to document
 * an API.
 *
 * @param kind          where its value comes from
 * @param name          the name of the path variable or of the query
 *                      parameter, e.g. "id" for ":id", null for the other
 *                      kinds
 * @param javaParameter the parameter of the Java method
 * @param defaultValue  the value of a query parameter when it is absent, as
 *                      it would be written in the query string, null if it
 *                      has none
 * @param required      whether it must be given: always for a path variable,
 *                      and for a query parameter declared so, a request
 *                      without it being rejected with 400
 * @param description   a description of a query parameter, null if it has
 *                      none
 * @see RouteHandler#getParameters()
 */
public record RouteParameter(Kind kind,
                             String name,
                             Parameter javaParameter,
                             String defaultValue,
                             boolean required,
                             String description) {

  public enum Kind {
    /**
     * A variable of the route, e.g. ":id", or its splat, e.g. "*path".
     */
    PATH,
    /**
     * A parameter of the query string.
     */
    QUERY,
    /**
     * The body of the request: a {@link Form}, or an object read by the
     * input parser of the handler, e.g. from JSON.
     */
    BODY,
    /**
     * Anything else: the {@link Request}, the {@link Response}, the whole
     * {@link Query}, or what a custom extractor provides.
     */
    OTHER
  }

  /**
   * The type of the parameter, e.g. <code>int</code>.
   */
  public Class<?> type() {
    return javaParameter.getType();
  }
}
