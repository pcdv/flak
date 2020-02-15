package flak.spi;

/**
 * A hook that is executed before the route handler. For example it can be
 * used to check that the user is authenticated before letting the request
 * go through.
 *
 * @author pcdv
 */
public interface BeforeHook {
  class StopProcessingException extends Exception {}

  /**
   * Throw this from execute() to reject a request. Youare supposed to set
   * a status and/or reply some data before doing so.
   */
  StopProcessingException STOP = new StopProcessingException();

  void execute(SPRequest request) throws StopProcessingException;
}
