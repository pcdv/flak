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
`flak-backend-jdk`   | Binding for the web server included in the JDK
`flak-backend-netty` | Binding for [Netty](https://netty.io/), see [Backends](docs/backends.md)
`flak-login`         | Add-on for managing authentication, see [Authentication](docs/login.md)
`flak-resource`      | Add-on for serving static resources, see [Static resources](docs/static-resources.md)
`flak-jackson`       | Add-on for conversion to/from JSON using Jackson, see [JSON](docs/json.md)
`flak-swagger`       | Add-on to generate OpenAPI specifications, see [OpenAPI](docs/openapi.md)
`flak-util`          | Misc utilities (e.g. route dumper)

## Getting started

Add the API and a backend to `build.gradle` (the badge above shows the latest
released version):

```groovy
repositories {
  maven { url = "https://jitpack.io" }
}

dependencies {
  implementation "com.github.pcdv.flak:flak-api:3.0"

  // the backend, see Backends for the netty alternative
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
[compact](flak-examples/src/main/java/flak/examples/HelloWorldCompact.java):

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

A slightly bigger taste, with a path variable, a query parameter and JSON:

```java
@Route("/api/users/:id/orders")
@JSON
public List<Order> orders(String id, @QueryParam(value = "limit", defaultValue = "20") int limit) {
  return store.orders(id, limit);
}

@Route("/api/users/:id/orders")
@Post
@JSON
public Order create(String id, Response r, Order order) {
  r.setStatus(201);
  return store.add(id, order);
}
```

## Features

The [documentation](docs/README.md) covers each of them in detail.

- **[Routing](docs/routing.md)**: `@Route` on any public method, one
  annotation per HTTP method (`@Post`, `@Put`, `@Patch`, `@Delete`, `@Head`,
  `@Options`), path variables (`/users/:id`) and splats (`/files/*path`),
  prefixes, and the routes of an app listed at runtime
- **[Handler arguments](docs/arguments.md)**: path variables, typed
  `@QueryParam` with defaults, `Query`, `Form`, `Request`, and arguments of
  your own types built by custom extractors
- **[Responses](docs/responses.md)**: return a `String`, bytes, a stream or
  any object through a formatter, set the status and headers, redirect,
  stream chunked output or Server-Sent Events
- **[Request bodies](docs/request-bodies.md)**: streamed to the handler,
  with a size limit per app or per handler
- **[Errors and hooks](docs/errors-and-hooks.md)**: throw an
  `HttpException` to answer with a status, error and success handlers, a
  handler for unknown URLs, hooks before every request
- **[Compression](docs/compression.md)**: gzip, per handler or class
- **[Several apps on one server](docs/apps-and-servers.md)**, each under
  its own path, **HTTPS**, bind address, thread pool
- **[JSON](docs/json.md)**: `@JSON` converts arguments and return values with
  Jackson
- **[Authentication](docs/login.md)**: sessions, login page,
  `@LoginRequired`, permissions with `@WithPermission`, custom
  authentication schemes
- **[Static resources](docs/static-resources.md)**: serve a directory or a
  folder of the classpath, optionally restricted to logged-in users
- **[OpenAPI](docs/openapi.md)**: generate a specification from the handlers
- **[Plugins](docs/plugins.md)**: installed automatically or listed
  explicitly, and easy to write
- **[Two backends](docs/backends.md)**: the JDK's HttpServer or Netty, with
  the same code. Flak can also plug into a Netty server you own, next to
  websockets.

## New in 3.0

Java 17, a complete Netty backend, streamed request bodies with size limits,
route handlers that can be configured at runtime, typed query parameters,
and explicit plugin lists. A few behaviours changed along the way, e.g. how
`+` is decoded in query strings, and which of 401 and 403 flak-login sends.
[Migrating to 3.0](docs/migration-3.0.md) lists everything, with what to do
about it.

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
 * the [request](flak-api/src/main/java/flak/Request.java)
 can be accessed through a ThreadLocal
 * user authentication is similar to [flask-login](https://flask-login.readthedocs.io/en/latest/)

But now the style differs quite a bit since objects can be automatically
passed in method arguments.

## History

Flak is a refactored fork of [JFlask](https://github.com/pcdv/jflask).

### Goals of the migration from JFlask
 * have a clean API, well separated from implementation
 * provide several back-ends:
 [flak-backend-jdk](flak-backend-jdk)
 and, since 3.0,
 [flak-backend-netty](flak-backend-netty)
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

The test suite runs against both backends: `./gradlew :flak-tests:test` for
the JDK one, `./gradlew :flak-tests:testNetty` for Netty.

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
