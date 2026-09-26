package flak.login;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handlers decorated with this annotation must check that the associated
 * user has specified permission. A user who is not logged in is treated as
 * with {@link LoginRequired}, one who lacks the permission gets 403.
 * <p>
 * On a class, it applies to all its route handlers, and to those of its
 * subclasses, except the ones that carry {@link WithPermission} or
 * {@link WithAnyPermission} themselves: the annotation of a method replaces
 * that of its class.
 *
 * @author pcdv
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface WithPermission {
  String value();
}
