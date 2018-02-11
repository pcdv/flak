package flask.test;

import java.io.IOException;

import flak.Form;
import flak.annotations.Route;
import flak.permissions.WithPermission;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author pcdv
 */
public class PermissionTest extends AbstractAppTest {

  @Route("/api/login")
  public void login(Form form) {
    app.getSessionManager().loginUser(form.get("login"));
  }

  @WithPermission("access")
  @Route("/api/data")
  public String getData() {
    return "OK";
  }

  @Test
  @Ignore // not implemented
  public void testPermission() throws IOException {
    TestUtil.assertFails(() -> client.get("/api/data"), "foo");
    client.post("/api/login", "login=joe");
  }
}
