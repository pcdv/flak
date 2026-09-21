package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the maximum size, in bytes, of the request body a route handler
 * accepts. A request announcing a larger body is rejected with 413 before
 * anything is read, and a body that turns out to be larger while being read
 * fails the same way.
 * <p>
 * This overrides the limit of the app, see
 * {@link flak.App#setMaxBodySize(long)}. Use {@link #UNLIMITED} for a handler
 * that deals with an arbitrarily large body itself, typically by streaming it
 * somewhere:
 * <pre>
 * &#64;Route("/api/import")
 * &#64;Post
 * &#64;MaxBodySize(MaxBodySize.UNLIMITED)
 * public void upload(Request r) throws IOException {
 *   try (OutputStream out = new FileOutputStream(tmp)) {
 *     IO.pipe(r.getInputStream(), out, false);
 *   }
 * }
 * </pre>
 * It can also be set on a class, which applies to all its route handlers.
 *
 * @author pcdv
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface MaxBodySize {

  /**
   * No limit: the handler is trusted to read the body without holding it all
   * in memory.
   */
  long UNLIMITED = -1;

  long value();
}
