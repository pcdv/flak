# Flak - A lightweight and modular web framework for Java

[![Release](https://jitpack.io/v/pcdv/flak.svg)](https://jitpack.io/#pcdv/flak)
[![build](https://github.com/pcdv/flak/actions/workflows/gradle.yml/badge.svg)](https://github.com/pcdv/flak/actions/workflows/gradle.yml)

Flak is a minimal but powerful framework for web applications and REST
services. Its main philosophy is keeping boilerplate to a minimum. It runs
either on the HttpServer embedded in the JDK, which costs no dependency at
all, or on [Netty](https://netty.io/).

Flak 3.0 and later require **Java 17** or later. If you are stuck on an older
JDK, the 2.x releases target Java 8.

It is composed of a generic API, two backends and some add-ons. In a minimal
setup, on top of the JDK backend, the total size of dependencies is around
60KiB. If you need to implement a REST server and handle JSON data, you will
have to add `jackson-databind` to your dependencies.

Flak components      | Description
-------------------- | -----------
`flak-api`           | Public API
`flak-spi`           | Internal API for service providers
`flak-backend-jdk`   | Binding for the web server included in JDK
`flak-backend-netty` | Binding for [Netty](https://netty.io/), see [Backends](#backends)
`flak-login`         | Add-on for managing authentication
`flak-resource`      | Add-on for serving static resources
`flak-jackson`       | Add-on for conversion to/from JSON using jackson
`flak-swagger`       | Add-on to dynamically generate OpenAPI specifications
`flak-util`          | Misc utilities (e.g. route dumper)

## Table of Contents

<!--ts-->
* [Flak - A lightweight and modular web framework for Java](#flak---a-lightweight-and-modular-web-framework-for-java)
   * [Table of Contents](#table-of-contents)
   * [New in 3.0](#new-in-30)
      * [Migrating from 2.x](#migrating-from-2x)
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
      * [Request bodies](#request-bodies)
      * [Plugins](#plugins)
      * [Backends](#backends)
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

## New in 3.0

 * **Java 17** is required, and the netty backend is finished: it passes the
   same test suite as the JDK one, serves HTTPS, and is published. See
   [Backends](#backends).
 * **Request bodies are streamed** to the route handlers instead of being read
   into memory, and their size is capped per app or per handler. See
   [Request bodies](#request-bodies).
 * **Plugins can be listed explicitly** with `AppFactory.setPlugins()` rather
   than only discovered in the classpath. See [Plugins](#plugins).
 * `App.addCustomExtractor()` is part of the public API. See
   [Custom arguments](#custom-arguments).
 * Flak can serve its routes from a netty server the application owns, leaving
   it free to serve websockets on the same port. See the
   [netty backend](https://github.com/pcdv/flak/tree/master/flak-backend-netty).

### Migrating from 2.x

Applications should build and run unchanged, with these exceptions.

**Java 17 or later.** The 2.x releases target Java 8 and remain available.

**Request bodies are capped at 16MiB**, where the JDK backend had no limit at
all. A handler that legitimately receives more has to say so:

```java
  @Route("/api/import")
  @Post
  @MaxBodySize(MaxBodySize.UNLIMITED)
  public void upload(Request r) throws IOException { ... }
```

Use `App.setMaxBodySize()` to change the default for a whole app.

**A request body can only be read once.** This was already true of the JDK
backend; the netty one used to allow a second read.

**404 and 500 responses now carry a short `text/plain` body** instead of being
empty, so that a client can tell an error from an empty document.

**A few classes moved.** They are internal, but some applications used them:

2.x                                     | 3.0
--------------------------------------- | ---
`flak.backend.jdk.FormImpl`             | `flak.spi.FormImpl`, in `flak-spi`
`flak.backend.jdk.BufferedOutputStream` | `flak.spi.util.BufferedOutputStream`, in `flak-spi`
`flak.backend.jdk.RouteDumper`          | `flak.util.RouteDumper`, still in `flak-util`

`flak-util` no longer depends on `flak-backend-jdk`, so `RouteDumper` now works
with any backend.

**For backend and plugin authors only:** `AppFactory` gained `setPlugins()` and
`SPRequest` gained `setMaxBodySize()`, so an implementation of either outside
the project needs those methods. `SPPlugin.install()` was added as a default
method and is now where a plugin registers its extractors, called by
`App.addPlugin()`. `AbstractApp.getMethodHandlers()` and
`AbstractMethodHandler.processResponse()`/`isApplicable()` are public.

## Getting started

### Hello World

Here is the obligatory
 [HelloWorld](https://github.com/pcdv/flak/blob/master/flak-examples/src/main/java/flak/examples/HelloWorld.java) application.

Here is the minimal set of dependencies that needs to be included in
`build.gradle` (the badge above shows the latest released version):

```groovy
repositories {
  maven { url = "https://jitpack.io" }
}

dependencies {
  implementation "com.github.pcdv.flak:flak-api:3.0"

  // the backend, see Backends below for the netty alternative
  runtimeOnly "com.github.pcdv.flak:flak-backend-jdk:3.0"
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

Route handlers can accept arguments. Like with [Flask](https://flask.palletsprojects.com/en/stable/quickstart/#routing),
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
 `App.addCustomExtractor()`, e.g.
 `app.addCustomExtractor(Token.class, req -> new Token(req.getHeader("X-Token")))`
 - specify an input format with the @InputFormat annotation (which requires
 prior declaration of an InputParser with App.addInputParser())
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

The idea is to create an [AppFactory](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/AppFactory.java)
then call `createApp(String)` with two separate paths. Then you can add your
route handlers and start them.

### Request bodies

The body of a request is streamed to the route handler: it is never held in
memory as a whole, so a handler can pipe an upload of any size straight to
its destination.

Because a handler that does keep the body in memory should not be at the
mercy of its client, the size is capped: 16MiB by default, changed for a
whole app with `App.setMaxBodySize()`, and overridden per handler with the
[@MaxBodySize](https://github.com/pcdv/flak/blob/master/flak-api/src/main/java/flak/annotations/MaxBodySize.java)
annotation. A request over the limit is rejected with 413, before its body is
read at all when it announces its length.

```java
  @Route("/api/import")
  @Post
  @MaxBodySize(MaxBodySize.UNLIMITED)
  public void upload(Request r) throws IOException {
    try (OutputStream out = new FileOutputStream(file)) {
      IO.pipe(r.getInputStream(), out, false);
    }
  }
```

The body can only be read once, whichever way it is read: `getInputStream()`,
a `Form` argument and a JSON argument all consume it.

### Plugins

Add-ons such as `flak-jackson` and `flak-login` are plugins. They are looked up
in the classpath with `ServiceLoader` and installed in every app created by the
factory, which is usually what you want.

When it is not, name the ones you need:

```java
AppFactory fac = Flak.getFactory();
fac.setPlugins(JacksonPlugin.class, FlakLogin.class);
App app = fac.createApp();
```

Automatic discovery is then disabled: exactly those plugins are installed, in
that order. Naming a plugin whose module is missing from the classpath fails
immediately, rather than leaving the app quietly short of a feature, and
`setPlugins()` with no argument installs no plugin at all.

A plugin of your own, which has no `FlakPluginLoader` to be discovered by, is
installed with `app.addPlugin(new MyPlugin(app))`.

### Backends

Route handling is independent from the HTTP server underneath. Two backends
are available, and the same test suite runs against both:

Backend              | Extra dependencies | Description
-------------------- | ------------------ | -----------
`flak-backend-jdk`   | none               | The [HttpServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html) included in the JDK. The default choice, and the lightest by far.
`flak-backend-netty` | ~3.4MiB            | [Netty](https://netty.io/), useful if it is already part of your stack.

A backend is discovered on the classpath with `ServiceLoader`, so switching
from one to the other is a matter of changing the `runtimeOnly` dependency:
application code is unchanged.

If both are on the classpath, `Flak.getFactory()` returns whichever comes
first. Pass a predicate to pick one explicitly:

```java
AppFactory factory = Flak.getFactory(cls -> cls.getName().contains("netty"));
```

Either backend can serve HTTPS: build a `javax.net.ssl.SSLContext` and pass it
to `factory.getServer().setSSLContext()` before starting the server.

Neither backend supports async responses: the response is sent as soon as the
route handler returns, so replying from another thread does not work.

### To be continued....

Other features that still need to be documented (until more documentation is
available, you can find examples in the
[junits](https://github.com/pcdv/flak/tree/master/flak-tests/src/test/java/flask/test)):
 * easy parsing of path arguments (e.g. `/api/todo/:id` or `/api/upload/*path`)
 * error handlers
 * HTTP redirection
 * pluggable user authentication
 * direct serving of static resources from a directory or jar
 * HTTPS support
 * ...

## Why Flak?

I'm a big fan of lightweight and simple. I've always liked the simplicity
of Flask applications and missed an equivalent solution for Java. Most existing
frameworks were very heavy in terms of dependencies 
(e.g. [Play](https://www.playframework.com/), 
[Spring Boot](https://spring.io/projects/spring-boot/), etc). 
[Spark](https://sparkjava.com/) was a better fit but it brings ~2.5MiB of
dependencies.

The JDK includes a [HTTP server](
https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html
)
that is perfectly suited for serving small applications but its API is rather 
painful. Flak allows to leverage it with a friendly API, and the same
application can run on Netty instead.


The API initially shared a lot of similarities with [Flask](https://flask.palletsprojects.com/):
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
 * provide several back-ends:
 [flak-backend-jdk](https://github.com/pcdv/flak/tree/master/flak-backend-jdk)
 and, since 3.0,
 [flak-backend-netty](https://github.com/pcdv/flak/tree/master/flak-backend-netty)
 based on [Netty](https://netty.io/). Other back-ends, e.g.
 [Jetty](https://jetty.org/), could be added the same way.
 * provide SSL support
 * optional plugins for user management, JSON serialization, CSRF protection...

## Build

Building Flak requires a JDK 17 or later. Everything else, including the
Gradle distribution itself, is downloaded by the wrapper:

```
./gradlew build
```

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