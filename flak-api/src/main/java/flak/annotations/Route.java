package flak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Route annotation binds a method with a route so that it is automatically
 * invoked when a request is submitted with a compatible URI and HTTP method.
 * <p>
 * Decorated methods can return the following types:
 * <ul>
 * <li> <code>String</code> : directly sent as response</li>
 * <li> <code>byte[]</code> : directly sent as response</li>
 * <li> <code>InputStream</code> : piped into response</li>
 * <li> <code>Response</code> : useful if the handler has already set status
 * and written data in response (no more processing will happen) </li>
 * <li> <code>void</code> : sends an empty response</li>
 * </ul>
 * <p>
 * They can accept the following argument types:
 * <ul>
 * <li>{@link flak.Request} &mdash; if you need to access the request</li>
 * <li>{@link flak.Response} &mdash; if you need to access the response (in that
 * case it is often preferable to return <code>void</code>)</li>
 * <li>{@link flak.Form} &mdash; to easily access the parameters of a POST
 * request</li>
 * <li>{@link flak.Query} &mdash; to easily access the query string</li>
 * <li><code>String</code> or <code>int</code>&mdash; if the route contains
 * some
 * "/:var" elements</li>
 * <li>any other type, provided an {@link InputFormat} annotation is
 * present</li>
 * </ul>
 * <p>
 * To register decorated methods into the App, {@link flak.App#scan(Object)}
 * must be called with an instance of the class containing the method.
 *
 * @author pcdv
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Route {

  /**
   * Specifies the route. Format can be:
   * <p>
   * <b>static</b>
   * <p>
   * <pre>
   * &#064;Route(&quot;/foo/bar&quot;)
   * public void fooBar() {
   *   return &quot;...&quot;;
   * }
   * </pre>
   * <p>
   * <b>variable</b>
   * <p>
   * <pre>
   * &#064;Route(&quot;/hello/:name&quot;)
   * public void hello(String name) {
   *   return &quot;Hello &quot; + name;
   * }
   * </pre>
   * <p>
   * <b>ending with a "splat"</b>
   * <p>
   * <pre>
   * &#064;Route(&quot;/file/*path&quot;)
   * public byte[] getFile(String path) throws IOException {
   *   if (path.contains(&quot;..&quot;))
   *     throw new IllegalArgumentException(&quot;Invalid path&quot;);
   *   return Files.readAllBytes(root.resolve(path));
   * }
   * </pre>
   */
  String value();

}
