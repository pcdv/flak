package flask.test;

import java.io.IOException;

import flak.Form;
import flak.Request;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.login.DefaultFlakSession;
import flak.login.DefaultUser;
import flak.login.FlakUser;
import flak.login.WithAnyPermission;
import flak.login.WithPermission;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pcdv
 */
public class PermissionTest extends AbstractAppTest {

  private long now = System.currentTimeMillis();

  @Override
  protected void preScan() {
    initFlakLogin();
    sessionManager.setTimeProvider(() -> now);

    FlakUser foo = sessionManager.createUser("foo");
    sessionManager.addUser(foo);

    FlakUser joe = sessionManager.createUser("joe");
    ((DefaultUser) joe).addPermission("access");
    sessionManager.addUser(joe);

    FlakUser root = sessionManager.createUser("admin");
    ((DefaultUser) root).addPermission("admin");
    sessionManager.addUser(root);

    app.scan(new AdminHandlers());
    app.scan(new InheritedAdminHandlers(), "/inherited");
  }

  /**
   * Every route of the class requires the permission, except the one that
   * says otherwise.
   */
  @WithPermission("admin")
  public static class AdminHandlers {
    @Route("/admin/data")
    public String getAdminData() {
      return "ADMIN";
    }

    @WithPermission("access")
    @Route("/admin/public")
    public String getPublicData() {
      return "PUBLIC";
    }
  }

  /**
   * The routes of a subclass are restricted by the annotation of its parent.
   */
  public static class InheritedAdminHandlers extends AdminHandlers {
  }

  @Route("/api/login")
  @Post
  @SuppressWarnings("deprecation")
  public void login(Form form) {
    FlakUser user = sessionManager.getUser(form.get("login"));
    if (user != null)
      sessionManager.openSession(app, user, app.getResponse());
  }

  @Route("/api/logout")
  public void logout(Request r) {
    sessionManager.closeCurrentSession(r);
  }

  @Route("/api/loginExpiring")
  @Post
  public void loginExpiring(Form form) {
    FlakUser user = sessionManager.getUser(form.get("login"));
    DefaultFlakSession session =
      new DefaultFlakSession(user, sessionManager.generateSessionToken(), now + 10000);
    sessionManager.openSession(app, session, app.getResponse());
  }

  @WithPermission("access")
  @Route("/api/data")
  public String getData() {
    return "OK";
  }

  @WithAnyPermission({"access", "admin"})
  @Route("/api/data2")
  public String getData2() {
    return "OK";
  }

  @Test
  public void testPermission() throws IOException {
    // not logged in
    TestUtil.assertFails(() -> client.get("/api/data"), "401");
    TestUtil.assertFails(() -> client.get("/api/data2"), "401");

    // unknown user, i.e. still not logged in
    client.post("/api/login", "login=unknown");
    TestUtil.assertFails(() -> client.get("/api/data"), "401");
    TestUtil.assertFails(() -> client.get("/api/data2"), "401");

    // logged in as user with no permission
    client.post("/api/login", "login=foo");
    TestUtil.assertFails(() -> client.get("/api/data"), "403 No permission");
    TestUtil.assertFails(() -> client.get("/api/data2"), "403 No permission");

    // logged in as user with required permission
    client.post("/api/login", "login=joe");
    Assert.assertEquals("OK", client.get("/api/data"));
    Assert.assertEquals("OK", client.get("/api/data2"));

    // logged in as user with other required permission
    client.post("/api/login", "login=admin");
    TestUtil.assertFails(() -> client.get("/api/data"), "403 No permission");
    Assert.assertEquals("OK", client.get("/api/data2"));
  }

  @Test
  public void testPermissionOnClass() throws IOException {
    for (String prefix : new String[]{"", "/inherited"}) {
      TestUtil.assertFails(() -> client.get(prefix + "/admin/data"), "401");

      client.post("/api/login", "login=joe");
      TestUtil.assertFails(() -> client.get(prefix + "/admin/data"), "403 No permission");
      // the annotation of the method replaces that of the class
      Assert.assertEquals("PUBLIC", client.get(prefix + "/admin/public"));

      client.post("/api/login", "login=admin");
      Assert.assertEquals("ADMIN", client.get(prefix + "/admin/data"));
      TestUtil.assertFails(() -> client.get(prefix + "/admin/public"), "403 No permission");

      client.get("/api/logout");
    }
  }

  @Test
  public void testExpiredSessionHasNoPermission() throws IOException {
    client.post("/api/loginExpiring", "login=joe");
    Assert.assertEquals("OK", client.get("/api/data"));

    now += 20000;
    TestUtil.assertFails(() -> client.get("/api/data"), "401");
  }
}
