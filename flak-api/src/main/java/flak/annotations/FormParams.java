package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a method parameter whose value is built from the fields of a form
 * posted as <code>application/x-www-form-urlencoded</code>: each field sets the
 * property of the same name, as {@link QueryParams} does with the query
 * string, e.g.
 * <pre>
 * public class Signup {
 *   public String email;
 *   public boolean newsletter;
 * }
 *
 * &#64;Route("/signup")
 * &#64;Post
 * public void signup(&#64;FormParams Signup signup)
 * </pre>
 * The form is the body of the request, so that the handler can take no other
 * body. <b>It requires flak-jackson</b>, which binds the properties as Jackson
 * does, so that the class needs no annotation from Flak. Without it, the
 * handler is rejected as soon as it is scanned.
 *
 * @since 3.2.0
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface FormParams {
}
