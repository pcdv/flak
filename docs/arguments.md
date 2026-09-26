# Handler arguments

A route handler declares the parameters it needs, and Flak supplies them,
in any order:

| Parameter | Value |
| --------- | ----- |
| `String`, `int` | a [path variable](routing.md#path-variables) |
| annotated with `@QueryParam` | a [query parameter](#query-parameters), converted to the type of the parameter |
| `Query` | the whole [query string](#the-query-string) |
| `Form` | a [form](#forms) sent in the body |
| `Request`, `Response` | [the request](#the-request), and its response |
| `FlakUser`, `SessionManager` | the logged-in user and the session manager, with [flak-login](login.md) |
| a type registered with `addCustomExtractor()` | whatever the [extractor](#custom-arguments) builds from the request |
| any other type | the [request body](#objects-parsed-from-the-body), parsed by an input format, e.g. JSON |

For example:

```java
@Route("/users/:id/orders")
public String orders(int id, @QueryParam("status") String status, Request req) { ... }
```

## Query parameters

`@QueryParam` binds a parameter to one value of the query string. For
`/items?q=shoes&limit=20`:

```java
@Route("/items")
public String search(@QueryParam("q") String q,
                     @QueryParam(value = "limit", defaultValue = "50") int limit) { ... }
```

Supported types are `String`, `String[]`, `int`, `long`, `double`,
`boolean`, their boxed counterparts (`Integer`, …), and enums. An enum value
is matched to the constant of the same name.

An absent parameter takes its `defaultValue` if it has one. Otherwise:

| Type | Absent value |
| ---- | ------------ |
| `String`, boxed types, enums | `null` |
| `String[]` | an empty array; a repeated parameter (`?tag=a&tag=b`) gives all its values |
| `boolean` | `false` |
| `int`, `long`, `double` | `-1` |

Use a boxed type, e.g. `Integer`, to tell a missing number from one of the
values it could take. An empty value (`?limit=`) counts as absent, except for
a `String`, which gets `""`.

A value that cannot be converted, such as `limit=abc`, or `flag=yes` for a
boolean, is rejected with **400** and a message naming the parameter. A
boolean must be `true` or `false`, in any case.

The default is checked when the handler is scanned, so an invalid default
fails at startup.

A `@QueryParam` also documents itself: the [OpenAPI generator](openapi.md)
lists it, with its type, default and description
(`@QueryParam(value = "q", description = "Search terms")`).

## The query string

A `Query` parameter gives the whole query string, parsed:

```java
@Route("/search")
public String search(Query q) {
  String text = q.get("q");                // null if absent
  String sort = q.get("sort", "date");     // with a default
  int page = q.getInt("page", 1);
  boolean exact = q.getBool("exact", false);
  String[] tags = q.getArray("tag");       // all occurrences
  ...
}
```

`q.parameters()` lists every name/value pair, in order, repeated names
included.

Names and values are decoded as HTML forms encode them: `+` and `%20` are
spaces, and `%2B` is a `+`. The query string is split before it is decoded,
so an encoded `&` or `=` (`%26`, `%3D`) stays inside its value. The raw query
string, as it was sent, is available from `request.getQueryString()`.

Unlike `@QueryParam`, `Query.getInt()` does not reject a value that is not a
number: it throws a `NumberFormatException`, which gives a 500.

## Forms

A `Form` parameter parses a body sent as `application/x-www-form-urlencoded`,
which is what an HTML form posts by default:

```java
@Route("/login")
@Post
public void login(Form form) {
  String user = form.get("user");
  ...
}
```

`Form` has the same methods as `Query` and decodes the same way. It reads the
body, which can only be read once (see [Request bodies](request-bodies.md)).
Malformed data, such as `%zz`, is rejected with 400.

`request.getForm()` gives the same object.

## The request

A `Request` parameter gives access to everything the request carries:

| Method | Returns |
| ------ | ------- |
| `getMethod()` | `GET`, `POST`, … |
| `getPath()` | the path, relative to the app, without the query string |
| `getQueryString()` | the query string as it was sent, or `null` |
| `getQuery()`, `getForm()` | see above |
| `getHeader(name)` | the first value of a header, or `null` |
| `getCookie(name)` | the value of a cookie, or `null` |
| `getRemoteAddress()` | the address of the client |
| `getInputStream()` | the body, see [Request bodies](request-bodies.md) |
| `getResponse()` | the response, see [Responses](responses.md) |
| `getHandler()` | the Java method serving the request |

A `Response` parameter is the same as `request.getResponse()`.

The request is also available from `app.getRequest()` on the thread serving
it. Code deep in a call chain can use it without having it passed along.

## Custom arguments

Any type can become a handler argument, given an extractor that builds it
from the request:

```java
app.addCustomExtractor(Token.class, req -> new Token(req.getHeader("X-Token")));

@Route("/api/data")
public String data(Token token) { ... }
```

The extractor runs on every request to a handler that takes the type, before
the handler is called. It may throw an `HttpException` to reject the request,
e.g. with 401 when the token is missing.

Register extractors before scanning the handlers that use them. An extractor
takes precedence over everything else, so registering one for `String` or
`int` would take over path variables.

## Objects parsed from the body

A parameter of any other type is parsed from the body of the request by an
**input parser**. The usual case is JSON, which [flak-jackson](json.md)
handles with a single annotation:

```java
@Route("/api/items")
@Post
@JSON
public Item create(Item item) { ... }
```

For another format, register a parser under a name, and name it on the
handlers that use it:

```java
app.addInputParser("CSV", (req, type) -> Csv.read(req.getInputStream(), type));

@Route("/api/import")
@Post
@InputFormat("CSV")
public String importItems(ItemList items) { ... }
```

Register the parser before scanning the handlers that name it.

Without a parser, a parameter of a type Flak does not know makes `scan()`
fail with "No @InputFormat or @JSON found".
