package flak;

/**
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
}
