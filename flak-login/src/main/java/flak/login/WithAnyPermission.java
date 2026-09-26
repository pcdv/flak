package flak.login;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handlers decorated with this annotation must check that the associated
 * user has any of specified permission. Applies to a class as
 * {@link WithPermission} does.
 *
 * @author pcdv
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface WithAnyPermission {
  String[] value();
}
