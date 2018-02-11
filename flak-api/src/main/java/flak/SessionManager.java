package flak;

import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public interface SessionManager {

  void createToken(String login, String token, boolean rememberMe);

  String getLogin(String token);

  boolean isTokenValid(String token);

  void removeToken(String token);

  void loginUser(String login);

  void logoutUser();

  String getCurrentLogin();

  void setRequireLoggedInByDefault(boolean b);

  boolean getRequireLoggedInByDefault();

  Response redirectToLogin();

  void setLoginPage(String path);

  void setSessionTokenCookie(String name);

  boolean checkLoggedIn(SPRequest r);
}
