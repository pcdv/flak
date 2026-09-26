# Routing

A route handler is a public method annotated with
[@Route](../flak-api/src/main/java/flak/annotations/Route.java). The value of
the annotation is the path the handler answers, relative to the root of the
app:

```java
public class Hello {
  @Route("/hello")
  public String hello() {
    return "Hello!";
  }
}

app.scan(new Hello());
```

## HTTP methods

A handler answers exactly one HTTP method. It is GET unless one of these
annotations says otherwise: `@Post`, `@Put`, `@Patch`, `@Delete`, `@Head`,
`@Options`.

```java
@Route("/items")
public String list() { ... }

@Route("/items")
@Post
public String create(Form form) { ... }
```

Two handlers can share a path if they answer different methods.

A GET handler does not answer HEAD requests. A path that must answer HEAD
needs a handler of its own with `@Head`, see [Responses](responses.md#head).

## Path variables

A segment starting with `:` is a variable. Its value is passed to the handler
as a `String` or `int` argument:

```java
@Route("/users/:name/orders/:id")
public String order(String name, int id) {
  return name + " " + id;
}
```

Variables are bound to parameters in order, not by name. The number of
unannotated `String` and `int` parameters must match the number of
variables; `scan()` fails otherwise. Other kinds of parameters, such as `Request` or `Query`, can
appear anywhere in the list (see [Handler arguments](arguments.md)).

Only `int` is supported, not `Integer`. A value that is not a number, e.g.
`/users/bob/orders/abc`, gets a 404: there is no such resource. To answer it
otherwise, take a `String` and parse it yourself.

## Splats

A last segment starting with `*` matches the rest of the path, slashes
included:

```java
@Route("/files/*path")
public byte[] file(String path) throws IOException {
  if (path.contains(".."))
    throw new HttpException(404, "Not found");
  return Files.readAllBytes(root.resolve(path));
}
```

- `/files/a/b.txt` gives `a/b.txt`.
- A trailing slash is kept: `/files/a/` gives `a/`.
- A splat needs at least one segment, so `/files` and `/files/` do not match.
  Add a separate `@Route("/files")` handler if they should.
- A splat can follow variables (`/env/:id/file/*path`), but only as the last
  segment of the route.

Validate a splat before using it as a file path, as in the example above.
[`app.serveDir()`](static-resources.md) does this for you when serving files.

## Matching rules

- The query string is ignored: `/hello?x=1` matches `@Route("/hello")`.
- A trailing slash in the request is ignored: `/hello/` matches
  `@Route("/hello")`.
- A fixed segment takes precedence over a variable: with `/api/list` and
  `/api/:id`, a request for `/api/list` goes to the first one.
- `/` and `/index.html` are two distinct routes.
- A request matching no route gets a 404, or goes to the unknown page
  handler if there is one (see [Errors and hooks](errors-and-hooks.md)).

## Prefixes and inheritance

`scan(obj, prefix)` binds the routes of an object under a prefix. This lets
one class serve several paths:

```java
app.scan(new ItemRoutes("books"), "/books");
app.scan(new ItemRoutes("music"), "/music");
```

`scan()` also picks up the public `@Route` methods that a class inherits.

## Errors when scanning

`scan()` checks each handler and throws a `ScanException` explaining what is
wrong. The mistake is reported at startup rather than on the first request.
It catches:

- more or fewer `String`/`int` parameters than path variables
- a splat that is not the last segment of the route
- a return type or parameter that Flak cannot handle without a formatter or
  a parser (see [Responses](responses.md) and [JSON](json.md))
- an `@OutputFormat` naming a formatter that was not registered
- a `@QueryParam` of an unsupported type, or with an invalid default value

## Listing the routes of an app

`app.getHandlers()` returns every route handler of the app, whatever the
backend. `app.getHandler(method, route)` looks one up, and fails if there is
none:

```java
app.getHandlers()
   .forEach(h -> System.out.println(h.getHttpMethod() + " " + h.getRoute()));

RouteHandler upload = app.getHandler("POST", "/api/import");
```

A [RouteHandler](../flak-api/src/main/java/flak/RouteHandler.java) provides:

- `getRoute()`: the route exactly as declared, relative to the app, variables
  included (e.g. `/users/:name`). Prepend `app.getPath()` for an absolute path.
- `getHttpMethod()`
- `getJavaMethod()`: the Java method it invokes, whose annotations can be read
- `getParameters()`: what each parameter of the method is bound to, as a
  [RouteParameter](../flak-api/src/main/java/flak/RouteParameter.java): a
  path variable, a query parameter with its default value, the body, or
  something else, such as the `Request`
- `setMaxBodySize()`: see [Request bodies](request-bodies.md)

`flak-util` provides a ready-made listing, grouped by path:

```java
System.out.print(new RouteDumper().dumpRoutes(app, new StringBuilder()));
```
