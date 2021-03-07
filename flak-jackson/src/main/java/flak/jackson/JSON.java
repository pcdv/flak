package flak.jackson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Automatically converts the return type to a JSON string (except if
 * the return type is String, byte[], InputStream ...) and if a non "basic"
 * parameter type is found in method at last position, parse the request's input as JSON.
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
 * Since 2.0.1, if the method needs to receive some JSON input but outputs something else
 * than JSON, you can annotate the last parameter of the method with the JSON annotation.
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
 * @author pcdv
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface JSON {

  /**
   * Optional ID passed to ObjectMapper provider. Allows the Jackson plugin
   * to return a different mapper for some requests.
   *
   * @see JacksonPlugin#setObjectMapperProvider(MapperProvider)
   */
  String value() default "";

  /**
   * Specifies the class to instantiate when parsing JSON from request body. It is not
   * mandatory to specify it but it allows to improve parsing performance by using a
   * specialized {@link com.fasterxml.jackson.databind.ObjectReader}.
   *
   * Note that if the class to parse is already specified as the last parameter of the
   * handler method, JacksonPlugin will automatically use it (unless the class is from
   * the "java.lang" package).
   */
  Class<?> inputClass() default Object.class;
}
