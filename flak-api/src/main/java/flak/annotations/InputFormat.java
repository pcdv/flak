package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import flak.InputParser;

/**
 * <p>
 * Indicates that the associated handler method should receive as last
 * argument an object parsed from contents of request and of specified
 * type.
 * <p>
 * Example
 * <pre>
 *   &#64;Put
 *   &#64;InputFormat("JSON", Foo.class)
 *   &#64;Route("/api/foo/:id")
 *   public String putFoo(String id, Foo obj) {
 * </pre>
 *
 * @author pcdv
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InputFormat {

  /**
   * Identifies the converter.
   *
   * @see flak.App#addInputParser(String, InputParser)
   */
  String name();

  /**
   * Specifies the type of the element that must be decoded and passed
   * to the handler as last argument.
   */
  Class type();
}
