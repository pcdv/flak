package flak.login;

import flak.Request;
import flak.Response;

import java.util.Objects;

public class SessionManagerDelegate implements SessionManager0 {

  private SessionManager0 sm;

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
  public void setLoginPage(String path) {
    sm.setLoginPage(path);
  }

  @Override
  public FlakSession getCurrentSession(Request r) {
    return sm.getCurrentSession(r);
  }

  public void setDelegate(SessionManager0 sm) {
    this.sm = Objects.requireNonNull(sm);
  }

  public SessionManager0 getDelegate() {
    return sm;
  }
}
