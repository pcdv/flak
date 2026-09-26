package samples;

import flak.App;
import flak.AppFactory;
import flak.Flak;
import flak.ResourceOptions;
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

    // static resources are served from the classpath, i.e. from the web-app
    // jar, or from the project dir when run from the IDE
    app.serveClasspath("/", "/app", new ResourceOptions().classLoader(WebApp.class.getClassLoader()));

    app.start();
    System.out.println("Listening on " + app.getRootUrl());
  }
}
