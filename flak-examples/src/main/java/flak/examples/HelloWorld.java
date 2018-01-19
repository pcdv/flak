package flak.examples;

import java.awt.Desktop;
import java.net.URI;

import flak.App;
import flak.Flak;
import flak.annotations.Route;

/**
 * Minimal Hello world web application.
 */
public class HelloWorld {

  @Route("/")
  public String helloWorld() {
    return "Hello world!";
  }

  public static void main(String[] args) throws Exception {
    App app = Flak.createHttpApp(8080);
    app.scan(new HelloWorld());
    app.start();
    Desktop.getDesktop().browse(new URI(app.getRootUrl()));
  }
}
