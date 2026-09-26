package flak;

/**
 * A hook that is executed before the route handler. For example it can be
 * used to check that the user is authenticated before letting the request
 * go through.
 *
 * @author pcdv
 * @see App#addBeforeAllHook(BeforeHook)
 */
public interface BeforeHook {
  class StopProcessingException extends Exception {}

  /**
   * Throw this from execute() to reject a request. You are supposed to set
   * a status and/or reply some data before doing so.
   */
  StopProcessingException STOP = new StopProcessingException();

  void execute(Request request) throws StopProcessingException;
}
