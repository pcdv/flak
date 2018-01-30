package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Automatically converts the return type to a JSON string (except if
 * the return type is String, byte[], InputStream ...) and if a non "basic"
 * parameter type is found in method, parse the request's input as JSON.
 *
 * This is equivalent to adding one or both of the following annotations:
 * <pre>
 * @OutputFormat("JSON")
 * @InputFormat("JSON")
 * </pre>
 *
 * @author pcdv
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JSON {}
