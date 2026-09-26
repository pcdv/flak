package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the associated route handler is valid for PATCH requests.
 * A route handler answers a single HTTP method: GET, unless one of these
 * annotations says otherwise, e.g.
 * <pre>
 *   &#64;Patch
 *   &#64;Route("/some/path")
 * </pre>
 *
 * @author pcdv
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Patch {}
