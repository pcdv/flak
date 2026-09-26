package flak;

/**
 * Notified when a route handler fails with an exception, i.e. when the
 * request is answered with 500. It is not called for a URL that matches no
 * route (see {@link UnknownPageHandler}), nor for an {@link HttpException},
 * whose status and message are what the handler meant to send.
 *
 * @author pcdv
 * @see App#addErrorHandler(ErrorHandler)
 */
public interface ErrorHandler {
  /**
   * Handles an error. It may write its own response, e.g. set another status
   * and a body; otherwise a short "Internal Server Error" is sent.
   *
   * @param status the request status sent back to client, i.e. 500
   * @param request the request sent by client
   * @param t the exception thrown by the route handler
   */
  void onError(int status, Request request, Throwable t);
}
