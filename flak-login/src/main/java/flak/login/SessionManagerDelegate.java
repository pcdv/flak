package flak.login;

import flak.App;
import flak.Request;
import flak.Response;

import java.util.Objects;

public class SessionManagerDelegate implements SessionManager {

  private SessionManager sm;

  public SessionManagerDelegate(SessionManager sm) {
    this.sm = sm;
  }

  @Override
  public void setRequireLoggedInByDefault(boolean b) {
    sm.setRequireLoggedInByDefault(b);
  }

  @Override
  public boolean getRequireLoggedInByDefault() {
    return sm.getRequireLoggedInByDefault();
  }

  @Override
  public void redirectToLogin(Response response) {
    sm.redirectToLogin(response);
  }

  @Override
  public boolean checkLoggedIn(Request r) {
    return sm.checkLoggedIn(r);
  }

  @Override
  public boolean isLoggedIn(Request r) {
    return sm.isLoggedIn(r);
  }

  @Override
  public void setLoginPage(String path) {
    sm.setLoginPage(path);
  }

  @Override
  public void setAuthTokenCookieName(String name) {
    sm.setAuthTokenCookieName(name);
  }

  @Override
  public FlakSession getCurrentSession(Request r) {
    return sm.getCurrentSession(r);
  }

  @Override
  public FlakSession openSession(App app, FlakUser user, Response r) {
    return sm.openSession(app, user, r);
  }

  @Override
  public void closeSession(FlakSession session) {
    sm.closeSession(session);
  }

  @Override
  public void closeCurrentSession(Request request) {
    sm.closeCurrentSession(request);
  }

  @Override
  public FlakUser getUser(String id) {
    return sm.getUser(id);
  }

  @Override
  public FlakUser createUser(String id) {
    return sm.createUser(id);
  }

  @Override
  public void addUser(FlakUser user) {
    sm.addUser(user);
  }

  @Override
  public String getAuthTokenCookieName() {
    return sm.getAuthTokenCookieName();
  }

  public void setDelegate(SessionManager sm) {
    this.sm = Objects.requireNonNull(sm);
  }
}
