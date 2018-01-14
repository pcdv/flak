package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can be used to decorate all URL handlers that do not require the user to be
 * logged in. Useful when the application is configured so as to require a
 * logged in user for all URLs by default.
 *
 * @author pcdv
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginNotRequired {}
