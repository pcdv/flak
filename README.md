# Flak - A *really* micro web framework for Java

Flak allows to easily write very lightweight web applications. The total size
of added dependencies is around 40KiB.

The API is inspired by [Flask](http://flask.pocoo.org/): annotations are used
to register route handlers.


## Getting started

Here is the obligatory
 [HelloWorld](https://github.com/pcdv/flak/blob/master/flak-examples/src/main/java/flak/examples/HelloWorld.java) application.

It requires the following dependencies:
```groovy
dependencies {
  compile 'com.github.pcdv.flak:flak-api:0.29'
  runtime 'com.github.pcdv.flak:flak-backend-jdk:0.29'
}
```

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

## Why Flak?

I'm a big fan of lightweight and simple. I've always liked the simplicity
of Flask applications and missed an equivalent solution for Java. Most existing
frameworks were very heavy in terms of dependencies 
(e.g. [Play](https://www.playframework.com/), 
[Sprint Boot](https://projects.spring.io/spring-boot/), etc). 
[Spark](http://sparkjava.com/) was more like it but it brings ~2.5MiB of
dependencies.

The JDK includes a [HTTPServer](http://docs.oracle.com/javase/7/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html)
that is perfectly suited for serving small applications but its API is rather 
painful. Flak allows to leverage it with a friendly API.

And most of all, it was fun to write!


## History

Flak is a refactored fork of [JFlask](https://github.com/pcdv/jflask).

### Goals of the migration
 * have a clean API, well separated from implementation
 * provide several backends (only one is available at this time: 
 [flak-backend-jdk](https://github.com/pcdv/flak/tree/master/flak-backend-jdk) but it will
 now possible to provide backends for [Netty](https://netty.io/), 
 [Jetty](https://www.eclipse.org/jetty/), etc.)
 * provide SSL support
 * optional plugins for user management, JSON serialization, CSRF protection...
 