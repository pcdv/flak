package net.jflask;

import java.util.Hashtable;
import java.util.Map;

import flak.SessionManager;

/**
 * Default, non-persistent, implementation of SessionManager.
 *
 * @author pcdv
 */
public class DefaultSessionManager implements SessionManager {

  private Map<String, String> sessions = new Hashtable<>();

  @Override
  public void createToken(String token, String login, boolean rememberMe) {
    // TODO: store more useful info about the user
    sessions.put(token, login);
  }

  @Override
  public String getLogin(String token) {
    return sessions.get(token);
  }

  @Override
  public boolean isTokenValid(String token) {
    return sessions.containsKey(token);
  }

  @Override
  public void removeToken(String token) {
    sessions.remove(token);
  }
}
