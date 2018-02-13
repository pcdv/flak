package flak;

/**
 * This exception can be thrown in route handlers. The corresponding
 * response code and response will be sent back to client.
 *
 * @author pcdv
 */
public class HttpException extends RuntimeException {
  private final int responseCode;

  public HttpException(int responseCode, String message) {
    super(message);
    this.responseCode = responseCode;
  }

  public int getResponseCode() {
    return responseCode;
  }

  @Override
  public String toString() {
    return getClass().getName() + " " + responseCode + " " + getMessage();
  }
}
