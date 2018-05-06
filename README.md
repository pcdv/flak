# Flak - A lightweight and modular web framework for Java

Flak allows to easily write lightweight web applications. It is composed
of a generic API, a default implementation and some add-ons. In a minimal 
setup, the total size of dependencies is around 40KiB.

Flak components    | Description
------------------ | -----------
`flak-api`         | Main API
`flak-backend-jdk` | Binding for the web server included in JDK
`flak-login`       | Add-on for managing user sessions
`flak-resource`    | Add-on for serving static resources
`flak-jackson`     | Add-on for conversion to/from JSON using jackson

## Table of Contents

  * [Getting started](#getting-started)
     * [Hello World](#hello-world)
     * [Managing apps](#managing-apps)
     * [Route handlers](#route-handlers)
     * [Return values](#return-values)
     * [Method arguments](#method-arguments)
        * [Path variables](#path-variables)
        * [Request argument](#request-argument)
        * [Query argument](#query-argument)
        * [Form argument](#form-argument)
        * [Custom arguments](#custom-arguments)
     * [To be continued....](#to-be-continued)
  * [Why Flak?](#why-flak)
  * [History](#history)
     * [Goals of the migration from JFlask](#goals-of-the-migration-from-jflask)
  * [A few words of warning](#a-few-words-of-warning)

## Getting started

### Hello World

Here is the obligatory
 [HelloWorld](https://github.com/pcdv/flak/blob/master/flak-examples/src/main/java/flak/examples/HelloWorld.java) application.

Here is the minimal set of dependencies needs to be included in `build.gradle`.
If you want to download jars by hand, you can find them
[here](https://bintray.com/paulcdv/maven).

```groovy
repositories {
  jcenter()
}

dependencies {
  compile "com.github.pcdv.flak:flak-api:1.0.0-beta5"
  runtime "com.github.pcdv.flak:flak-backend-jdk:1.0.0-beta5"
}
```

The following application outputs "Hello world!" on `http://localhost:8080`:

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

### Managing apps

The example above allocates a web server for a single application. However,
it is possible to host several Flak apps on a single server, for example
one located at path `/app1` and another one at `/app2`.

The idea is to create a [FlakFactory](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/AppFactory.java)
then call `createApp(String)` with two separate paths. Then you can add your
route handlers and start them.

### Route handlers

A route handler is a public method annotated with [@Route](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/annotations/Route.java).
As in Python Flask, the route's argument specifies the path to which the
handler must be bound (relative to the root path of the application).

It is associated with only one HTTP method which is GET by default. To
associate it with another method, just add the @Post, @Put, @Delete or any
other annotation.

Route handlers can be defined in any class. Scan an instance of the
class with
[App](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/App.java).scan()
so all handlers can be discovered.

### Return values

Route handlers can return the following basic types:
 - `String` : directly returned in response
 - `byte[]` : directly returned in response
 - `InputStream` : piped into response
 - `void` : returns an empty document

You can return any other type provided an [OutputFormatter](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/OutputFormatter.java)
is specified. Use the [@OutputFormat](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/annotations/OutputFormat.java)
annotation to specify which formatter to use.

Note that the formatter is referenced by name and needs to have been registered
before with `App.addOutputFormatter()`.

If what you need is to convert the returned object to JSON, you can simply
use the `Jackson` plugin and add the [@JSON](https://github.com/pcdv/flak/blob/master/flak-jackson/src/main/java/flak/jackson/JSON.java)
annotation.

### Method arguments

Route handlers can accept arguments. Like with [Flask](http://flask.pocoo.org/docs/1.0/quickstart/#routing),
arguments can be extracted from the request's path. But there is more.

#### Path variables

If the path contains variable (e.g. `/api/:arg1/:arg2`), they are
automatically split, converted and passed as method arguments. The route handler
must have the same number of `int` or `String` arguments. For example:

```java
  @Route("/db/hello/:name")
  public String hello(String name) {
    return "Hello " + name;
  }
```

#### Request argument

Each HTTP call is wrapped in a [Request](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/Request.java).
You can access the request by simply adding a Request argument in your method,
e.g.

```java
  @Route("/api/stuff")
  public String getStuff(Request req) {
    return "You submitted param1=" + req.getQuery().get("param1");
  }
```

#### Query argument

If you only need to access the query string, the above example can be
simplified to:

```java
  @Route("/api/stuff")
  public String getStuff(Query q) {
    return "You submitted param1=" + q.get("param1");
  }
```

The query corresponds to arguments that are present in request URL, after '?',
e.g. `/api/stuff?param1=42`

#### Form argument

Similar to the example above, if you are in a POST route handler and need to
access arguments in `application/x-www-form-urlencoded` format, you can use
a [Form](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/Form.java)
argument.

See the following [example](https://github.com/pcdv/flak/blob/master/flak-tests/src/test/java/flask/test/FormTest.java).

#### Custom arguments

You can accept other argument types if you:
 - associate the type with an extractor using method
 AbstractApp.addCustomExtractor() (this is not in official API yet)
 - specify an input format with the @InputFormat annotation (which requires
 prior declaration of an InputParser with App.addInputParser().


### To be continued....

Other features that still need to be documented (until more documentation is
available, you can find examples in the
[junits](https://github.com/pcdv/flak/tree/master/flak-tests/src/test/java/flask/test)
):
 * easy parsing of path arguments (e.g. `/api/todo/:id` or `/api/upload/*path`)
 * error handlers
 * HTTP redirection
 * pluggable user authentication
 * direct serving of static resources from directory or jar
 * HTTPS support (experimental)
 * ...

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
 