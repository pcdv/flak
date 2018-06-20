package flak;

/**
 * @author pcdv
 */
public class ScanException extends RuntimeException {
  public ScanException(String message, Exception cause) {
    super(message, cause);
  }
}
