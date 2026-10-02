package flak.websocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * A websocket connection, seen from the server. Modelled on the WebSocket of
 * Java-WebSocket (org.java_websocket), so that code written for it ports with
 * few changes.
 * <p>
 * Its methods can be called from any thread.
 *
 * @author pcdv
 * @see WebSocketEndpoint
 */
public interface WebSocket {

  /**
   * Sends a text message.
   *
   * @throws IllegalStateException if the websocket is no longer open, i.e.
   * closing or closed
   * @throws java.io.UncheckedIOException if the connection broke, in which
   * case it is closed
   */
  void send(String text);

  /**
   * Sends a binary message. Same as {@link #send(String)} otherwise.
   */
  void send(byte[] data);

  /**
   * Sends the remaining bytes of the buffer as a binary message, without
   * changing its position. Same as {@link #send(String)} otherwise.
   */
  void send(ByteBuffer data);

  /**
   * Sends a ping, which the client answers with a pong.
   */
  void sendPing();

  /**
   * Closes the websocket normally, i.e. with {@link CloseFrame#NORMAL}.
   */
  void close();

  /**
   * Closes the websocket with specified code, e.g. {@link CloseFrame#GOING_AWAY}.
   */
  void close(int code);

  /**
   * Starts the closing handshake: once it is called, no message can be sent,
   * the messages still arriving are dropped, and
   * {@link WebSocketEndpoint#onClose} is called when the client answers,
   * or when the connection drops. Does nothing if the websocket is already
   * closing or closed.
   *
   * @param code a code from {@link CloseFrame}, or between 3000 and 4999 for
   * the application's own
   * @param message the reason, at most 123 bytes once encoded in UTF-8
   * @throws IllegalArgumentException if the code cannot be sent, e.g.
   * {@link CloseFrame#ABNORMAL_CLOSE}, or the message is too long
   */
  void close(int code, String message);

  boolean isOpen();

  /**
   * Tells whether the closing handshake is in progress.
   */
  boolean isClosing();

  boolean isClosed();

  InetSocketAddress getRemoteSocketAddress();

  InetSocketAddress getLocalSocketAddress();

  /**
   * Returns the URI the client requested, path and query string, e.g.
   * "/chat/lobby?nick=bob".
   */
  String getResourceDescriptor();

  /**
   * Returns the subprotocol agreed in the handshake, or null if none was.
   *
   * @see WebSocketEndpoint#setProtocols(String...)
   */
  String getProtocol();

  /**
   * Returns the object attached to this websocket, typically the session or
   * the user it serves.
   *
   * @see WebSocketEndpoint#accept(flak.Request, Object)
   */
  <T> T getAttachment();

  <T> void setAttachment(T attachment);
}
