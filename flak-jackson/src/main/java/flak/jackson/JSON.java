package flak.jackson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Automatically converts the return type to a JSON string (except if
 * the return type is String, byte[], InputStream ...) and if a non "basic"
 * parameter type is found in method, parse the request's input as JSON.
 * <p>
 * Note that this annotation implies the presence of an OutputFormatter with
 * name "JSON".
 * <p>
 * This is equivalent to adding one or both of the following annotations:
 * <pre>
 * &#064;OutputFormat("JSON")
 * &#064;InputFormat("JSON")
 * </pre>
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
 * @author pcdv
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JSON {

  /**
   * Optional ID passed to ObjectMapper provider. Allows the Jackson plugin
   * to return a different mapper for some requests.
   *
   * @see JacksonPlugin#setObjectMapperProvider(MapperProvider)
   */
  String value() default "";
}
