package flask.test;

import flak.Request;
import flak.login.DefaultFlakSession;
import flak.login.DefaultSessionManager;
import flak.login.FlakSession;
import flak.login.FlakUser;

import java.util.Base64;

public class BasicAuthSessionManager extends DefaultSessionManager {

  @Override
  public boolean isLoggedIn(Request r) {
    return super.isLoggedIn(r) || isBasicAuthValid(r) != null;
  }

  private String isBasicAuthValid(Request re) {
    String auth = re.getHeader("Authorization");
    if (auth != null && auth.startsWith("Basic ")) {
      String s = new String(Base64.getDecoder().decode(auth.substring(6)));
      int pos = s.indexOf(':');
      if (pos != -1) {
        String login = s.substring(0, pos);
        String pass = s.substring(pos + 1);

        FlakUser user = getUser(login);
        if (user != null && isPasswordValid(user, pass))
          return login;
      }
    }
    return null;
  }

  private boolean isPasswordValid(FlakUser user, String pass) {
    return "secret".equals(pass);
  }

  @Override
  public FlakSession getCurrentSession(Request r) {
    FlakSession session = super.getCurrentSession(r);
    if (session == null) {
      String login = isBasicAuthValid(r);
      if (login != null) {
        FlakUser user = getUser(login);
        if (user != null)
          return new DefaultFlakSession(user, "---basic-auth---");
      }
    }
    return session;
  }
}
