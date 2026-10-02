package flak.websocket;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.nio.channels.SocketChannel;

import com.sun.net.httpserver.HttpExchange;
import flak.spi.util.Log;
import sun.misc.Unsafe;

/**
 * The streams of the connection under an exchange of the JDK's HttpServer,
 * which its API does not expose: the hack this add-on rests on.
 * <p>
 * They are the fields <code>ris</code> and <code>ros</code> of
 * sun.net.httpserver.ExchangeImpl, found in the field <code>impl</code> of the
 * exchange the handler receives, and the socket is the field
 * <code>chan</code> of its <code>connection</code>. The request headers were
 * read from the input stream, so that nothing the client sent after them is
 * lost, and both streams go through TLS on an HttpsServer. Writing to them
 * directly, the server never learns that a response was sent: it leaves the
 * connection to the handler until the exchange is closed.
 * <p>
 * They are read with plain reflection when the JVM runs with
 * <code>--add-opens jdk.httpserver/sun.net.httpserver=ALL-UNNAMED</code>, and
 * with sun.misc.Unsafe otherwise. The latter needs no flag, but JDK 24 and
 * later print a warning the first time it is used.
 *
 * @author pcdv
 */
final class RawStreams {

  final InputStream in;

  final OutputStream out;

  /**
   * The socket under the streams, closed to drop the connection: unlike
   * closing the streams, which may send a TLS close_notify, it never blocks.
   */
  final SocketChannel channel;

  private RawStreams(InputStream in, OutputStream out, SocketChannel channel) {
    this.in = in;
    this.out = out;
    this.channel = channel;
  }

  static RawStreams of(HttpExchange exchange) {
    // HttpExchangeImpl or HttpsExchangeImpl, which both delegate to an
    // ExchangeImpl
    Object impl = read(exchange, "impl");
    return new RawStreams((InputStream) read(impl, "ris"),
                          (OutputStream) read(impl, "ros"),
                          (SocketChannel) read(read(impl, "connection"), "chan"));
  }

  private static Object read(Object obj, String name) {
    Field field;
    try {
      field = obj.getClass().getDeclaredField(name);
    }
    catch (NoSuchFieldException e) {
      throw new IllegalStateException(
        "Unsupported HttpServer implementation, no field " + name + " in " +
        obj.getClass().getName(), e);
    }

    try {
      field.setAccessible(true);
      return field.get(obj);
    }
    catch (InaccessibleObjectException | IllegalAccessException e) {
      return UnsafeAccess.read(obj, field);
    }
  }

  /**
   * In a class of its own, so that Unsafe is not touched while --add-opens
   * makes it unnecessary.
   */
  private static final class UnsafeAccess {

    private static final Unsafe UNSAFE = load();

    private static Unsafe load() {
      Log.debug("sun.net.httpserver is not open, reading it with Unsafe");
      try {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
      }
      catch (ReflectiveOperationException | RuntimeException e) {
        throw new IllegalStateException(
          "Cannot reach the connection of HttpServer: run the JVM with " +
          "--add-opens jdk.httpserver/sun.net.httpserver=ALL-UNNAMED", e);
      }
    }

    static Object read(Object obj, Field field) {
      return UNSAFE.getObject(obj, UNSAFE.objectFieldOffset(field));
    }
  }
}
