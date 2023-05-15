package flak.login;

/**
 * Defines a session, identified by a token generally passed in an HTTP cookie.
 */
public interface FlakSession {

  /**
   * Returns the associated token (sensitive information).
   */
  String getAuthToken();

  /**
   * Returns the associated user.
   */
  FlakUser getUser();

  /**
   * Returns the timestamp at which the session will expire. A zero value indicates
   * that the session never expires.
   *
   * @since 2.4
   */
  long getExpiry();

  /**
   * If true (by default), the HttpOnly attribute will be added in the Set-Cookie header.
   */
  boolean isHttpOnly();

  /**
   * If non-null (default=Strict), the SameSite attribute will be set to this value in the Set-Cookie header.
   */
  SameSite getSameSite();

  enum SameSite {
    None, Strict, Lax
  }
}
