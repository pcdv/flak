# Handler arguments

A route handler declares the parameters it needs, and Flak supplies them,
in any order:

| Parameter | Value |
| --------- | ----- |
| `String`, `int` | a [path variable](routing.md#path-variables) |
| annotated with `@QueryParam` | a [query parameter](#query-parameters), converted to the type of the parameter |
| annotated with `@QueryParams` | an [object built from the query string](#objects-built-from-the-query-string), requires [flak-jackson](json.md), since 3.1.0 |
| `Query` | the whole [query string](#the-query-string) |
| `Form` | a [form](#forms) sent in the body |
| annotated with `@FormParams` | an [object built from a form](#objects-built-from-a-form), requires [flak-jackson](json.md), since 3.2.0 |
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

A parameter the handler cannot do without is declared `required`: a request
without it is rejected with **400** "Missing query parameter q" before the
handler is called. It counts as absent as above, so an empty value is
rejected, except for a `String`. A required parameter cannot have a default.

```java
public String search(@QueryParam(value = "q", required = true) String q) { ... }
```

A `@QueryParam` also documents itself: the [OpenAPI generator](openapi.md)
lists it, with its type, default, whether it is required and its
description (`@QueryParam(value = "q", description = "Search terms")`).

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

## Objects built from the query string

*Since Flak 3.1.0, and requires [flak-jackson](json.md).*

When a handler takes many query parameters, or the same ones as other
handlers, gather them in a class, and annotate the parameter with
`@QueryParams`. Each query parameter sets the property of the same name. For
`/items?q=shoes&limit=20&tag=a&tag=b`:

```java
public class Search {
  public String q;
  public int limit = 50;     // when absent
  public List<String> tag;   // all occurrences
  public Color color;
}

@Route("/items")
public String search(@QueryParams Search search) { ... }
```

[flak-jackson](json.md) builds the object: the query string is turned into a
JSON object, one string per parameter or an array of them for a repeated
one, which Jackson binds as it binds a body. So the class needs no annotation
from Flak, and can stay in a module that does not depend on it, e.g. one
shared with a client. Public fields, setters, records and `@JsonCreator`
constructors all work, and the values are converted as Jackson converts
strings: numbers, `true`/`false`, enums by name, and so on. Without
flak-jackson, `scan()` rejects the handler, so that a missing dependency
shows at startup.

Jackson's annotations, when the class needs any, also apply here:

- `@JsonProperty("user.name")` gives the parameter another name than the
  property
- `@JsonProperty(required = true)` rejects a request without the parameter
  with **400** "Missing query parameter user.name"
- `@JsonIgnore` leaves a property out

As with `@QueryParam`, an empty value (`?limit=`) counts as absent, except
for a `String`, and a value that cannot be converted, such as `limit=abc`,
is rejected with **400** and a message naming the parameter. So is a
repeated parameter bound to a property that is not a collection or an
array. Parameters that match no property are ignored.

The mapper is that of the handler, the default one or the one named by its
`@JSON("id")` (see [Configuring Jackson](json.md#configuring-jackson)). So an
application can teach it its own annotations, rather than adding Jackson's
to its classes, with an `AnnotationIntrospector`:

```java
// names the properties after an annotation of the application, e.g. the one
// that also names the options of its command line
public class OptIntrospector extends JacksonAnnotationIntrospector {
  @Override
  public PropertyName findNameForDeserialization(Annotated a) {
    Opt opt = a.getAnnotation(Opt.class);
    return opt != null ? PropertyName.construct(opt.name())
                       : super.findNameForDeserialization(a);
  }
}
```

The [OpenAPI generator](openapi.md) lists each property as a query
parameter, with its type, its initial value as default, whether it is
required and its description, e.g. from `@JsonPropertyDescription`.

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

## Objects built from a form

*Since Flak 3.2.0, and requires [flak-jackson](json.md).*

`@FormParams` builds an object from the fields of a form, as
[`@QueryParams`](#objects-built-from-the-query-string) does from the query
string, with the same rules: properties named as Jackson names them, values
converted as it converts strings, a repeated field for a collection, an
empty value absent except for a `String`, unknown fields ignored, and 400
for a missing required field or a value that cannot be converted.

```java
public class Signup {
  @JsonProperty(required = true)
  public String email;
  public boolean newsletter;
}

@Route("/signup")
@Post
public void signup(@FormParams Signup signup) { ... }
```

The form is the body of the request, so that the handler cannot also take a
`Form` or another body. It can take `@QueryParams`.

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
