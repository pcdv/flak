package flak.util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * An event of a Server-Sent Events stream, as a browser's
 * <code>EventSource</code> receives it:
 * <pre>
 * SseEvent.data(json).name("price").id("42")
 * </pre>
 * Immutable: every setter returns a new event, so that one event can be
 * published to several clients.
 *
 * @see SseStream
 * @see SseBroadcaster
 * @since 3.5.0
 */
public final class SseEvent {

  private final String data;

  private final String name;

  private final String id;

  private final Duration retry;

  private SseEvent(String data, String name, String id, Duration retry) {
    this.data = data;
    this.name = name;
    this.id = id;
    this.retry = retry;
  }

  /**
   * Creates an event carrying specified data, which may span several lines.
   * An event without a name is a "message" event, which
   * <code>EventSource.onmessage</code> receives.
   */
  public static SseEvent data(String data) {
    return new SseEvent(Objects.requireNonNull(data, "data"), null, null, null);
  }

  /**
   * Sets the type of the event, which pages listen to with
   * <code>addEventListener(name, ...)</code>.
   */
  public SseEvent name(String name) {
    return new SseEvent(data, checkLine("name", name), id, retry);
  }

  /**
   * Sets the id of the event. A browser that reconnects sends the last id it
   * received in the Last-Event-ID header, see
   * {@link SseStream#getLastEventId()}.
   */
  public SseEvent id(String id) {
    checkLine("id", id);
    if (id.indexOf('\0') >= 0)
      throw new IllegalArgumentException("id must not contain NUL");
    return new SseEvent(data, name, id, retry);
  }

  /**
   * Sets how long the browser waits before reconnecting once the stream is
   * lost. It keeps this delay for the next reconnections too.
   */
  public SseEvent retry(Duration retry) {
    if (retry.isNegative())
      throw new IllegalArgumentException("retry must not be negative");
    return new SseEvent(data, name, id, retry);
  }

  public String getData() {
    return data;
  }

  public String getName() {
    return name;
  }

  public String getId() {
    return id;
  }

  public Duration getRetry() {
    return retry;
  }

  /**
   * Returns the event as it is sent: one line per field, each line of the data
   * in a "data:" line of its own, and a blank line to end the event.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (name != null)
      sb.append("event: ").append(name).append('\n');
    if (id != null)
      sb.append("id: ").append(id).append('\n');
    if (retry != null)
      sb.append("retry: ").append(retry.toMillis()).append('\n');
    appendLines(sb, "data: ", data);
    return sb.append('\n').toString();
  }

  byte[] encode() {
    return toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Encodes a comment, which browsers ignore: it keeps the connection busy,
   * so that proxies do not close it, and the server finds out when the client
   * is gone.
   */
  static byte[] comment(String text) {
    StringBuilder sb = new StringBuilder();
    appendLines(sb, ":", text);
    return sb.append('\n').toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * The lines of an event end with CR, LF or CRLF: a field that contains one
   * of them must be split.
   */
  private static void appendLines(StringBuilder sb, String prefix, String text) {
    for (String line : text.split("\r\n|\r|\n", -1))
      sb.append(prefix).append(line).append('\n');
  }

  private static String checkLine(String field, String value) {
    if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
      throw new IllegalArgumentException(field + " must fit on one line");
    return value;
  }
}
