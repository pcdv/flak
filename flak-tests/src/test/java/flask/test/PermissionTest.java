package flask.test;

import java.io.IOException;

import flak.Form;
import flak.annotations.Post;
import flak.annotations.Route;
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
  @Override
  protected void preScan() {
    initFlakLogin();

    FlakUser foo = sessionManager.createUser("foo");
    sessionManager.addUser(foo);

    FlakUser joe = sessionManager.createUser("joe");
    ((DefaultUser) joe).addPermission("access");
    sessionManager.addUser(joe);

    FlakUser root = sessionManager.createUser("admin");
    ((DefaultUser) root).addPermission("admin");
    sessionManager.addUser(root);
  }

  @Route("/api/login")
  @Post
  public void login(Form form) {
    FlakUser user = sessionManager.getUser(form.get("login"));
    if (user != null)
      sessionManager.openSession(app, user, app.getResponse());
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
    TestUtil.assertFails(() -> client.get("/api/data"), "No permission");
    TestUtil.assertFails(() -> client.get("/api/data2"), "No permission");

    // unknown user
    client.post("/api/login", "login=unknown");
    TestUtil.assertFails(() -> client.get("/api/data"), "No permission");
    TestUtil.assertFails(() -> client.get("/api/data2"), "No permission");

    // logged in as user with no permission
    client.post("/api/login", "login=foo");
    TestUtil.assertFails(() -> client.get("/api/data"), "No permission");
    TestUtil.assertFails(() -> client.get("/api/data2"), "No permission");

    // logged in as user with required permission
    client.post("/api/login", "login=joe");
    Assert.assertEquals("OK", client.get("/api/data"));
    Assert.assertEquals("OK", client.get("/api/data2"));

    // logged in as user with other required permission
    client.post("/api/login", "login=admin");
    TestUtil.assertFails(() -> client.get("/api/data"), "No permission");
    Assert.assertEquals("OK", client.get("/api/data2"));
  }
}
