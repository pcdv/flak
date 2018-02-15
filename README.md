# Flak - A lightweight and modular web framework for Java

Flak allows to easily write lightweight web applications. It is composed
of a generic API, a default implementation and some add-ons. In a minimal 
setup, the total size of dependencies is around 40KiB.

Flak components | Description
------------ | -------------
`flak-api`         | Contains the API and various utilities
`flak-backend-jdk` | Binding for the web server included in JDK
`flak-login`       | Add-on for managing user sessions
`flak-resource`    | Add-on for serving static resources
`flak-jackson`     | Add-on for conversion to/from JSON using jackson

More add-ons are on the way.

## Getting started

Here is the obligatory
 [HelloWorld](https://github.com/pcdv/flak/blob/master/flak-examples/src/main/java/flak/examples/HelloWorld.java) application.

```groovy
repositories {
  jcenter()
}

dependencies {
  compile 'com.github.pcdv.flak:flak-api:0.30'
  runtime 'com.github.pcdv.flak:flak-backend-jdk:0.30'
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

Or if you like it 
[compact](https://github.com/pcdv/flak/blob/master/flak-examples/src/main/java/flak/examples/HelloWorldCompact.java):
```java
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
```

## Why Flak?

I'm a big fan of lightweight and simple. I've always liked the simplicity
of Flask applications and missed an equivalent solution for Java. Most existing
frameworks were very heavy in terms of dependencies 
(e.g. [Play](https://www.playframework.com/), 
[Sprint Boot](https://projects.spring.io/spring-boot/), etc). 
[Spark](http://sparkjava.com/) was a better fit but it brings ~2.5MiB of
dependencies.

The JDK includes a [HTTP server](
http://docs.oracle.com/javase/7/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html
)
that is perfectly suited for serving small applications but its API is rather 
painful. Flak allows to leverage it with a friendly API and in the future will
support other back-ends.


The API initially shared a lot of similarities with [Flask](http://flask.pocoo.org/):
 * route handlers are methods with annotations like `@Route`, `@Post`, 
 `@LoginRequired` etc.
 * the [request](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/Request.java)
 can be accessed through a ThreadLocal
 * user authentication is similar to [flask-login](https://flask-login.readthedocs.io/en/latest/) 

But now the style differs quite a bit since objects can be automatically
passed in method arguments.

## Features

This deserves more documentation but here is a non-exhaustive list:
 * route handlers for any HTTP method
 * handlers can return: String, byte[], InputStream, Response ...
 * easy parsing of path arguments (e.g. `/api/todo/:id` or `/api/upload/*path`)
 * on-the-fly parsing to/from JSON or other formats
 * error handlers
 * easy HTTP redirection
 * pluggable user authentication
 * direct serving of static resources from directory or jar
 * HTTPS support (experimental)

Until more documentation is available, you can find examples in the 
[junits](https://github.com/pcdv/flak/tree/master/flak-tests/src/test/java/flask/test).


## History

Flak is a refactored fork of [JFlask](https://github.com/pcdv/jflask).

### Goals of the migration from JFlask
 * have a clean API, well separated from implementation
 * provide several back-ends (only one is available at this time: 
 [flak-backend-jdk](https://github.com/pcdv/flak/tree/master/flak-backend-jdk) but it will
 now possible to provide backends for [Netty](https://netty.io/), 
 [Jetty](https://www.eclipse.org/jetty/), etc.)
 * provide SSL support
 * optional plugins for user management, JSON serialization, CSRF protection...
 
 ## A few words of warning
 
 Flak is perfectly suited for small web applications but is not ready for use
 in an internet-facing application. Until now, the focus has been on ease of 
 use and conciseness, not performance or security!
 