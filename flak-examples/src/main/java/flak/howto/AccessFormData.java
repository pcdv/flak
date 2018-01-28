package flak.howto;

import flak.App;
import flak.Request;
import flak.annotations.Post;
import flak.annotations.Route;

/**
 * Example of how form data can be parsed in a POST request.
 *
 * @author pcdv
 */
public class AccessFormData {

  private final App app;

  public AccessFormData(App app) {
    this.app = app;
  }

  @Post
  @Route("/api/form")
  public String postForm() {
    Request req = app.getRequest();
    String param1 = req.getForm("param1");
    return "You submitted param1=" + param1;
  }
}
