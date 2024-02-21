# Flak - A lightweight and modular web framework for Java

[![Release](https://jitpack.io/v/pcdv/flak.svg)](https://jitpack.io/#pcdv/flak)

Flak is a minimal but powerful framework that leverages the HttpServer 
embedded in the JDK. Its main philosophy is keeping boilerplate to a minimum.

It is composed of a generic API, a default implementation and some add-ons. 
In a minimal setup, the total size of dependencies is around 40KiB. If you 
need to implement a REST server and handle JSON data, you will have to add
`jackson-databind` to your dependencies.

Flak components    | Description
------------------ | -----------
`flak-api`         | Public API
`flak-spi`         | Internal API for service providers
`flak-backend-jdk` | Binding for the web server included in JDK
`flak-login`       | Add-on for managing authentication
`flak-resource`    | Add-on for serving static resources
`flak-jackson`     | Add-on for conversion to/from JSON using jackson
`flak-swagger`     | Add-on to dynamically generate OpenAPI specifications

## Table of Contents

<!--ts-->
* [Flak - A lightweight and modular web framework for Java](#flak---a-lightweight-and-modular-web-framework-for-java)
   * [Table of Contents](#table-of-contents)
   * [Getting started](#getting-started)
      * [Hello World](#hello-world)
      * [Route handlers](#route-handlers)
      * [Return values](#return-values)
      * [Method arguments](#method-arguments)
         * [Path variables](#path-variables)
         * [Request argument](#request-argument)
         * [Query argument](#query-argument)
         * [Form argument](#form-argument)
         * [Custom arguments](#custom-arguments)
      * [Compression](#compression)
      * [Managing apps](#managing-apps)
      * [To be continued....](#to-be-continued)
   * [Why Flak?](#why-flak)
   * [History](#history)
      * [Goals of the migration from JFlask](#goals-of-the-migration-from-jflask)
   * [Build](#build)
      * [How to publish locally](#how-to-publish-locally)

<!-- Created by https://github.com/ekalinin/github-markdown-toc -->
<!-- Added by: pcdv, at: Sun Dec 31 19:04:56     2023 -->

<!--te-->
<!-- to update TOC:
 gh-md-toc --insert README.md
-->

## Getting started

### Hello World

Here is the obligatory
 [HelloWorld](https://github.com/pcdv/flak/blob/master/flak-examples/src/main/java/flak/examples/HelloWorld.java) application.

Here is the minimal set of dependencies needs to be included in `build.gradle`.

```groovy
repositories {
  maven { url "https://jitpack.io" }
}

dependencies {
  compile "com.github.pcdv.flak:flak-api:2.7.0"
  runtime "com.github.pcdv.flak:flak-backend-jdk:2.7.0"
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

Note that from version 2.7.0, you can also do:

```java
  @Route("/api/stuff")
  public String getStuff(@QueryParam("param1") String p1) {
    return "You submitted param1=" + p1;
  }
```

One advantage of this style is that the OpenAPI generator can automatically
take into account the parameter without additional boilerplate.

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
 - a common case is to decode an object serialized as JSON in the body
 of the request. You could do the following:
 
```java
  @Route("/api/jsonMap")
  @Post
  @JSON
  public Map postMap(Map map) {
    map.put("status", "ok");
    return map;
  }
```
 
### Compression

Gzip compression can be enabled for a given endpoint or all endpoints of a 
class by using the `@Compress` annotation. Alternatively, it can be enabled
using method `Response.setCompressionAllowed(true)`.

Files served with `FlakResourceImpl` will be automatically compressed 
according to their content type and size.

It is possible to tune compression behavior:
 * file size threshold (using system property `flak.compressThreshold`)
 * eligible content types (using a custom `ContentTypeProvider`)

### Managing apps

The example above allocates a web server for a single application. However,
it is possible to host several Flak apps on a single server, for example
one located at path `/app1` and another one at `/app2`.

The idea is to create a [FlakFactory](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/AppFactory.java)
then call `createApp(String)` with two separate paths. Then you can add your
route handlers and start them.

### To be continued....

Other features that still need to be documented (until more documentation is
available, you can find examples in the
[junits](https://github.com/pcdv/flak/tree/master/flak-tests/src/test/java/flask/test)):
 * easy parsing of path arguments (e.g. `/api/todo/:id` or `/api/upload/*path`)
 * error handlers
 * HTTP redirection
 * pluggable user authentication
 * direct serving of static resources from a directory or jar
 * HTTPS support (experimental)
 * ...

## Why Flak?

I'm a big fan of lightweight and simple. I've always liked the simplicity
of Flask applications and missed an equivalent solution for Java. Most existing
frameworks were very heavy in terms of dependencies 
(e.g. [Play](https://www.playframework.com/), 
[Spring Boot](https://projects.spring.io/spring-boot/), etc). 
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
 now be possible to provide backends for [Netty](https://netty.io/),
 [Jetty](https://www.eclipse.org/jetty/), etc.)
 * provide SSL support
 * optional plugins for user management, JSON serialization, CSRF protection...

## Build

### How to publish locally

If your project uses the local Ivy repository, run:
```
./gradlew publish -Pversion=3.0-SNAPSHOT
```

If your project uses the local Maven repository, run:
```
./gradlew publishToMavenLocal -Pversion=3.0-SNAPSHOT
```

Then use version `3.0-SNAPSHOT` in your project dependencies.