package flak.login;

/**
 * @author pcdv
 */
public class DefaultFlakSession implements FlakSession {
  private final FlakUser user;

  private final String token;

  public DefaultFlakSession(FlakUser user, String token) {this.user = user;
    this.token = token;
  }

  @Override
  public String getAuthToken() {
    return token;
  }

  @Override
  public FlakUser getUser() {
    return user;
  }
}
