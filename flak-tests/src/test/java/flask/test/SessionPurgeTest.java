package flask.test;

import flak.login.DefaultFlakSession;
import flak.login.DefaultSessionManager;
import flak.login.DefaultUser;
import flak.login.FlakSession;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Checks that expired sessions do not pile up in memory when nobody presents
 * them again.
 */
public class SessionPurgeTest {

  private long now = 1_000_000;

  private final List<String> closed = new ArrayList<>();

  private final DefaultSessionManager sessions = new DefaultSessionManager() {
    @Override
    public void closeSession(FlakSession session) {
      closed.add(session.getAuthToken());
      super.closeSession(session);
    }
  };

  private void open(String token, long expiry) {
    sessions.addSession(new DefaultFlakSession(new DefaultUser("joe"), token, expiry));
  }

  @Test
  public void testExpiredSessionsArePurged() {
    sessions.setTimeProvider(() -> now);

    open("a", now + 10_000);
    open("forever", 0);
    now += 20_000;
    // "a" has expired, but was purged less than a minute ago: not yet
    open("b", now + 3_600_000);
    assertEquals(3, sessions.getSessionCount());

    now += 60_000;
    open("c", now + 3_600_000);
    assertEquals(3, sessions.getSessionCount());
    // through closeSession(), so that a subclass persisting them knows
    assertEquals(List.of("a"), closed);
    assertEquals(null, sessions.getSessionForToken("a"));
  }
}
