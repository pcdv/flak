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

  StopProcessingException STOP = new StopProcessingException();

  void execute(SPRequest request) throws StopProcessingException;
}
