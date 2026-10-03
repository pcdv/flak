package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a method parameter whose value is built from the whole query
 * string: each query parameter sets the property of the same name, e.g. for
 * <code>/api/items?q=shoes&amp;limit=20&amp;tag=a&amp;tag=b</code>
 * <pre>
 * public class Search {
 *   public String q;
 *   public int limit = 50;
 *   public List&lt;String&gt; tag;
 * }
 *
 * &#64;Route("/api/items")
 * public String list(&#64;QueryParams Search search)
 * </pre>
 * <b>It requires flak-jackson</b>, which binds the properties as Jackson
 * does, so that the class needs no annotation from Flak. Without it, the
 * handler is rejected as soon as it is scanned.
 *
 * @since 3.1.0
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParams {
}
