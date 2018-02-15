package flak.howto;

import flak.Form;
import flak.annotations.Post;
import flak.annotations.Route;

/**
 * Example of how form data can be parsed in a POST request.
 */
public class AccessFormData {
  @Post
  @Route("/api/form")
  public String postForm(Form form) {
    return "You submitted param1=" + form.get("param1");
  }
}
