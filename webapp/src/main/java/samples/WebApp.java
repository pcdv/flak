package samples;

import flak.App;
import flak.AppFactory;
import flak.Flak;
import flak.annotations.Route;

public class WebApp {

  public static void main(String[] args) throws Exception {
    AppFactory fac = Flak.getFactory();
    fac.setPort(Integer.getInteger("port", 8080));
    App app = fac.createApp();

    // NB: it is cleaner to define route handler in dedicated classes
    app.scan(new Object() {
      @Route("/hello/:name")
      public String hello(String name) {
        return "Hello " + name;
      }
    });

    // static resources are served from local file system or directly
    // from the web-app jar
    app.servePath("/", "app/", WebApp.class.getClassLoader(), false);

    app.start();
    System.out.println("Listening on " + app.getRootUrl());
  }
}
