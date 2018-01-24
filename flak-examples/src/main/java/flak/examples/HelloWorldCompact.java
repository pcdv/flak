package flak.examples;

import flak.Flak;
import flak.annotations.Route;

/**
 * Minimal Hello world web application.
 */
public class HelloWorldCompact {
  public static void main(String[] args) throws Exception {
    Flak.createHttpApp(8080).scan(new Object() {
      @Route("/")
      public String helloWorld() {
        return "Hello world!";
      }
    }).start();
  }
}
