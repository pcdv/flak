# Flak


Flak is a refactored fork of [JFlask](https://github.com/pcdv/jflask).

## Goals
 * have a clean API, well separated from implementation
 * provide several backends (only one is available at this time: 
 [flak-backend-jdk](https://github.com/pcdv/flak/tree/master/flak-backend-jdk) but it will
 now possible to provide backends for [Netty](https://netty.io/), 
 [Jetty](https://www.eclipse.org/jetty/), etc.)
 * provide optional plugins for user management, JSON serialization, SSL 
 support etc.
 
## Status
 ```diff
 + All features and tests have been ported
 + A minimal JSON plugin is available (to be extended)
 - Artifacts have not yet been published on JCenter
 - Documentation to be written
 - More plugins to be written
 ```

## Hello world

Here is the obligatory
 [HelloWorld](https://github.com/pcdv/jflask/tree/master/flak/flak-examples/src/main/java/flak/examples/HelloWorld.java) application.

```java
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
```
