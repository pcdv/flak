package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates method parameters that must be extracted from the query string.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParam {
  /**
   * Required for all parameters of basic type (String, int, etc.)
   * Indicates the name of the query parameter that must be extracted.
   */
  String value();

  /**
   * Optional description of the parameter. Useful only for OpenAPI generation.
   * NB: it is planned to be able to extract this information from javadoc too.
   */
  String description() default "";
}
