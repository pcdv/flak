package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates method parameters that must be extracted from the query string,
 * e.g.
 * <pre>
 * &#64;Route("/api/items")
 * public String list(&#64;QueryParam("q") String q,
 *                    &#64;QueryParam(value = "limit", defaultValue = "50") int limit)
 * </pre>
 * Supported types are <code>String</code>, <code>String[]</code> (all the
 * occurrences of a repeated parameter), <code>int</code>, <code>long</code>,
 * <code>double</code>, <code>boolean</code>, their boxed counterparts, and
 * enums (by constant name).
 * <p>
 * A parameter that is absent is rejected with 400 if it is
 * {@link #required()}, and takes its {@link #defaultValue()} if there is
 * one. Otherwise it is <code>null</code>, an empty array for
 * <code>String[]</code>, <code>false</code> for a <code>boolean</code> and -1
 * for the other primitive types: use a boxed type to tell a missing number
 * from any value it could take. An empty value (<code>limit=</code>) counts as
 * absent, except for a <code>String</code>, which gets the empty string.
 * <p>
 * A value that cannot be converted, e.g. <code>limit=abc</code>, is rejected
 * with 400.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParam {
  /**
   * The value of {@link #defaultValue()} when none is given. Not meant to be
   * used directly.
   */
  String NO_DEFAULT = "flak:no-default";

  /**
   * Indicates the name of the query parameter that must be extracted.
   */
  String value();

  /**
   * Optional description of the parameter. Useful only for OpenAPI generation.
   * NB: it is planned to be able to extract this information from javadoc too.
   */
  String description() default "";

  /**
   * The value to use when the parameter is absent or empty, written as it
   * would be in the query string, e.g. "50" for an <code>int</code>. It is
   * checked when the handler is scanned, so that an invalid default fails
   * early.
   */
  String defaultValue() default NO_DEFAULT;

  /**
   * Whether the parameter must be given: when it is absent, or empty for a
   * type other than <code>String</code>, the request is rejected with 400
   * before the handler is called. It cannot have a {@link #defaultValue()}.
   */
  boolean required() default false;
}
