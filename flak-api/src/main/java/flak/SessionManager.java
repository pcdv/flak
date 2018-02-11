package flak;

import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public interface SessionManager {

  void loginUser(String login);

  void logoutUser();

  String getCurrentLogin();

  void setRequireLoggedInByDefault(boolean b);

  boolean getRequireLoggedInByDefault();

  void redirectToLogin(Response response);

  void setLoginPage(String path);

  void setSessionCookieName(String name);

  boolean checkLoggedIn(SPRequest r);
}
