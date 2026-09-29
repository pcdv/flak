package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps a '+' of the query string as a '+', as flak 2 did, instead of
 * decoding it as a space like a browser form. For a handler whose existing
 * clients send a '+' without encoding it, typically in a time zone offset:
 * <pre>
 * &#64;Route("/api/stop")
 * &#64;Post
 * &#64;KeepPlus
 * public void stop(&#64;QueryParam("maturity") String maturity) { ... }
 * </pre>
 * <code>?maturity=2024-02-01T00:00+02:00</code> then gives
 * <code>2024-02-01T00:00+02:00</code>. Everything else is decoded as usual:
 * "%2B" is a '+' and "%20" a space. The body of a form is not affected.
 * <p>
 * This is a concession to existing clients rather than a feature: new ones
 * should encode their values. It can also be put on a class, for all its
 * handlers.
 *
 * @author pcdv
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface KeepPlus {
}
