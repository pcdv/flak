package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables compression on an endpoint, or all endpoints of a class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Compress {
  int COMPRESS_THRESHOLD = Integer.getInteger("flak.compressThreshold", 1024);
}
