package flak.jackson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Automatically converts the return type to a JSON string (except if
 * the return type is String, byte[], InputStream ...) and parses the request
 * body as JSON into the parameter that flak binds to it, if any.
 * <p>
 * <b>Example</b>
 * <pre>
 * &#064;Put
 * &#064;Route("/api/foo")
 * &#064;JSON
 * public Foo putFoo(Foo foo) {
 *   return foo;
 * }
 * </pre>
 *
 * If the method needs to receive some JSON input but outputs something else
 * than JSON, annotate the body parameter rather than the method.
 * <p>
 * <b>Example</b>
 * <pre>
 * &#064;Put
 * &#064;Route("/api/foo")
 * public String putFoo(&#064;JSON Foo foo) {
 *   return "custom format";
 * }
 * </pre>
 *
 * On a class, it applies to all the route handlers the class declares, except
 * those annotated themselves, e.g. with another mapper.
 *
 * @author pcdv
 */
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JSON {

  /**
   * Optional ID of the ObjectMapper to use, for handlers that need other
   * Jackson settings than the default mapper.
   *
   * @see JacksonPlugin#registerMapper(String, com.fasterxml.jackson.databind.ObjectMapper)
   */
  String value() default "";

  /**
   * Specifies the class to instantiate when parsing JSON from request body.
   * Only needed when it cannot be taken from the type of the body parameter,
   * which is used by default.
   */
  Class<?> inputClass() default Object.class;
}
