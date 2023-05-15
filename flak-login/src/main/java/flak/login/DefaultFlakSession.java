package flak.login;

/**
 * Default implementation of FlakSession. There is currently no known reason to
 * provide another implementation.
 */
public class DefaultFlakSession implements FlakSession {
  private final FlakUser user;

  private final String token;
  private final long expiry;
  private SameSite sameSite = SameSite.Strict;
  private boolean httpOnly = true;

  public DefaultFlakSession(FlakUser user, String token) {
    this(user, token, 0);
  }

  public DefaultFlakSession(FlakUser user, String token, long expiry) {
    this.user = user;
    this.token = token;
    this.expiry = expiry;
  }

  @Override
  public String getAuthToken() {
    return token;
  }

  @Override
  public FlakUser getUser() {
    return user;
  }

  @Override
  public long getExpiry() {
    return expiry;
  }

  @Override
  public boolean isHttpOnly() {
    return httpOnly;
  }

  public void setHttpOnly(boolean httpOnly) {
    this.httpOnly = httpOnly;
  }

  @Override
  public SameSite getSameSite() {
    return sameSite;
  }

  public void setSameSite(SameSite sameSite) {
    this.sameSite = sameSite;
  }

  @Override
  public String toString() {
    return "DefaultFlakSession{user=" + user + '}';
  }
}
