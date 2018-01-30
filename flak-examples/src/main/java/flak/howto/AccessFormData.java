package flak.howto;

import flak.Request;
import flak.annotations.Post;
import flak.annotations.Route;

/**
 * Example of how form data can be parsed in a POST request.
 *
 * @author pcdv
 */
public class AccessFormData {

  @Post
  @Route("/api/form")
  public String postForm(Request req) {
    return "You submitted param1=" + req.getForm("param1");
  }
}
