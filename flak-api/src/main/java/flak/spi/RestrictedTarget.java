package flak.spi;

/**
 * Can be implemented by some classes implementing some route handlers.
 * Allows to indicate whether routes require a logged-in session. This
 * is a wart that allows to decouple flak-resource and flak-login.
 *
 * @author pcdv
 */
public interface RestrictedTarget {

  /**
   * Returns true if the resources contained in current object must be
   * restricted to logged-in users.
   */
  boolean isRestricted();
}
