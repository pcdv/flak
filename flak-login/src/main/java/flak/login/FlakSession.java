package flak.login;

/**
 * @author pcdv
 */
public interface FlakSession {
  String getAuthToken();

  FlakUser getUser();
}
