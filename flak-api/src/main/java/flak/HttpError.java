package flak;

/**
 * This exception can be thrown within method handlers to respond
 * with a given HTTP status and message.
 *
 * @author pcdv
 */
public class HttpError extends RuntimeException {
  private final int status;

  public HttpError(int status, String message) {
    super(message);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
