package flak.websocket;

/**
 * The status codes of the closing handshake (RFC 6455, section 7.4), with the
 * names Java-WebSocket gives them in its own CloseFrame.
 *
 * @author pcdv
 */
public final class CloseFrame {

  public static final int NORMAL = 1000;

  public static final int GOING_AWAY = 1001;

  public static final int PROTOCOL_ERROR = 1002;

  /**
   * The endpoint received a type of data it cannot accept.
   */
  public static final int REFUSE = 1003;

  /**
   * Received when the client closes without giving a code. Never sent.
   */
  public static final int NOCODE = 1005;

  /**
   * The connection dropped without a closing handshake. Never sent.
   */
  public static final int ABNORMAL_CLOSE = 1006;

  /**
   * A text message was not valid UTF-8.
   */
  public static final int NO_UTF8 = 1007;

  public static final int POLICY_VALIDATION = 1008;

  /**
   * A message exceeded {@link WebSocketEndpoint#setMaxMessageSize(long)}.
   */
  public static final int TOOBIG = 1009;

  public static final int EXTENSION = 1010;

  public static final int UNEXPECTED_CONDITION = 1011;

  private CloseFrame() {}

  /**
   * Tells whether a code may travel in a close frame: the ones defined by the
   * RFC, apart from those reserved for reporting a closure locally, and the
   * range 3000-4999 left to libraries and applications.
   */
  static boolean canBeSent(int code) {
    return (code >= 1000 && code <= 1003)
           || (code >= 1007 && code <= 1014)
           || (code >= 3000 && code <= 4999);
  }
}
