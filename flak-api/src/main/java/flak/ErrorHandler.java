package flak;

/**
 * @author pcdv
 */
public interface ErrorHandler {
  /**
   * Handles an error (either a 404 error due to invalid URL or a 500 error
   * due to an exception thrown by a handler).
   *
   * @param status the request status sent back to client
   * @param request the request sent by client
   * @param t optional error (null in case of 404)
   */
  void onError(int status, Request request, Throwable t);
}
