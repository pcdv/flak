package flak.spi;

/**
 * Can be implemented by some classes implementing some route handlers.
 * Allows to indicate whether routes require a logged-in session, without
 * annotations: this is how static resources served by {@link AbstractApp}
 * tell flak-login that they are restricted.
 *
 * @see SPPlugin#enforcesRestrictions()
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
