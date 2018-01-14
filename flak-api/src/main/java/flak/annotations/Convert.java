package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the associated handler method should have its return value
 * converted with specified converter (which is supposed to have been
 * registered in the flak App).
 * Instead of
 * <pre>
 *   &#64;Route(value="/some/path", method="POST", converter="JSON)
 * </pre>
 * you can use
 * <pre>
 *   &#64;Post
 *   &#64;Convert("JSON")
 *   &#64;Route("/some/path")
 * </pre>
 *
 * @author pcdv
 * @see flak.App
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Convert {
  /**
   * Specifies how the value returned by method must be converted and written
   * to the response stream. The given value must correspond to the name of a
   * converter registered in the App.
   */
  String value();
}
