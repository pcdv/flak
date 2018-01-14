package flak;

/**
 * @author pcdv
 */
public interface SessionManager {

  void createToken(String login, String token, boolean rememberMe);

  String getLogin(String token);

  boolean isTokenValid(String token);

  void removeToken(String token);
}
