import flak.App;
import flak.AppFactory;
import flak.Flak;
import flak.annotations.Route;

public class HelloWorld {

  public HelloWorld() throws Exception {
    AppFactory fac = Flak.getFactory();
    fac.setHttpPort(8080);
    App app = fac.createApp();

    app.scan(new Object() {
      @Route("/hello/:name")
      public String hello(String name) {
        return "Hello " + name;
      }
    });

    app.servePath("/", "app/", getClass().getClassLoader(), false);
    app.start();

    System.out.println("Listening on http://0.0.0.0:" + fac.getHttpPort());
  }

  /**
   * New example using the Flask clone.
   */
  public static void main(String[] args) throws Exception {
    new HelloWorld();
  }
}
