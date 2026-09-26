# Migrating to 3.0

## What's new

- **Java 17** is required. The Netty backend is complete: it passes the same
  test suite as the JDK one, serves HTTPS, and is published. See
  [Backends](backends.md).
- **Request bodies are streamed** to the route handlers instead of being read
  into memory, and their size is capped per app or per handler. See
  [Request bodies](request-bodies.md).
- **Route handlers can be looked up and configured** at runtime with
  `App.getHandler()` and `App.getHandlers()`, whatever the backend.
- **Plugins can be listed explicitly** with `AppFactory.setPlugins()` rather
  than only discovered on the classpath. See [Plugins](plugins.md).
- **`@QueryParam` supports more types**, `long`, `double`, `boolean`, their
  boxed counterparts and enums, and a `defaultValue`. See
  [Handler arguments](arguments.md#query-parameters).
- **`@WithPermission` and `@WithAnyPermission` work on classes.** They
  could be put there before, but were ignored.
- **Serving static files is part of the API**, with `App.serveDir()` and
  `App.serveClasspath()`. `flak-resource` is gone. See
  [Static resources](static-resources.md).
- `App.addCustomExtractor()` is part of the public API.
- Flak can serve its routes from a Netty server the application owns,
  leaving it free to serve websockets on the same port.

## Migrating from 2.x

Most applications build and run unchanged. The changes below are ordered
from the most to the least likely to affect you.

### Build

**Java 17 or later.** The 2.x releases target Java 8 and remain available.

**A few classes moved.** They are internal, but some applications used them:

| 2.x | 3.0 |
| --- | --- |
| `flak.backend.jdk.FormImpl` | `flak.spi.FormImpl`, in `flak-spi` |
| `flak.backend.jdk.BufferedOutputStream` | `flak.spi.util.BufferedOutputStream`, in `flak-spi` |
| `flak.backend.jdk.RouteDumper` | `flak.util.RouteDumper`, still in `flak-util` |

`flak-util` no longer depends on `flak-backend-jdk`, so `RouteDumper` works
with any backend.

**`flak-resource` was removed**: static files are served by the app itself.
Drop the dependency and replace `FlakResourceImpl`:

| 2.x | 3.0 |
| --- | --- |
| `new FlakResourceImpl(app).serveDir(url, dir)` | `app.serveDir(url, dir)` |
| `servePath(url, dir)`, for a directory | `app.serveDir(url, new File(dir))` |
| `servePath(url, path)`, for the classpath | `app.serveClasspath(url, path)` |
| `servePath(url, path, loader, restricted)` | `app.serveClasspath(url, path, new ResourceOptions().classLoader(loader).restricted())` |
| `setContentTypeProvider(provider)` | `new ResourceOptions().contentTypes(provider)` |
| `flak.plugin.resource.ContentTypeProvider`, `DefaultContentTypeProvider` | `flak.ContentTypeProvider`, `flak.DefaultContentTypeProvider`, in `flak-api` |

`servePath()` served a directory if one existed at that path, and the
classpath otherwise, so a typo in a directory name silently became a
classpath lookup. The two cases are now separate methods. Restricted
resources now fail to be served without flak-login, rather than being left
open to anyone.

**Listing the route handlers of an app no longer needs the backend.**
`App.getHandlers()` returns them all and `App.getHandler()` looks one up.
They replace reaching into `JdkApp` and its contexts, by reflection or
otherwise. `JdkApp.getHandlers()` returned contexts rather than handlers; it
is now named `getContexts()`, so code calling it no longer compiles.

### Request bodies are capped at 16MiB

The JDK backend used to have no limit. A handler that legitimately receives
more must now say so, or its clients get 413:

```java
@Route("/api/import")
@Post
@MaxBodySize(MaxBodySize.UNLIMITED)
public void upload(Request r) throws IOException { ... }
```

Use `App.setMaxBodySize()` to change the default for a whole app. Look for
handlers that read `getInputStream()`: uploads of files, archives or
database dumps are the usual suspects.

**A request body can only be read once.** This was already true of the JDK
backend; the Netty one used to allow a second read.

### Query strings are decoded like forms

**`+` in a query string is now a space**, as in a form posted by a browser.
It used to be kept as `+`. Browsers encode spaces as `+` when submitting a
form with GET, so such forms now receive what the user typed. A client that
puts a literal `+` in a query value without encoding it, e.g. a regular
expression, a version like `1.0+build` or a time zone offset, now sends a
space instead. Such clients must encode their values:

- in Java, with `URLEncoder.encode(value, UTF_8)`
- in JavaScript, with `encodeURIComponent()`, not `encodeURI()`, which leaves
  `+`, `&` and `=` as they are

**An encoded `&` or `=` stays inside its value.** The query string used to be
decoded before being split, so `?url=%2Fa%3Fx%3D1%26y%3D2` gave `url=/a?x=1`
plus a stray `y=2`. It now gives `url=/a?x=1&y=2`.

**`Request.getQueryString()` returns the query string as it was sent**,
still encoded. It used to return it decoded. Code that parses it by hand
should use `getQuery()` instead.

Names are decoded as well as values, and `Form.parameters()` and
`getArray()` return decoded values, like `get()`.

Code that builds a `FormImpl` from a raw query string should pass
`urlDecode = true`: it now decodes after splitting.

### Status codes

**401 and 403 were swapped** in `flak-login`. A user who is not logged in now
gets 401 (it was 403) when a route requires login and there is no login page.
A logged-in user who lacks a permission gets 403 (it was 401). Clients and
tests that test for the old codes need updating.

**A permission implies a logged-in user.** A user who is not logged in and
asks for a route with `@WithPermission` is now treated as for
`@LoginRequired`: redirected to the login page, or given 401. An expired
session used to keep its permissions on a route that did not also require
login; it no longer does.

**A status set by a handler is kept when it returns a body.** Returning a
`String`, `byte[]` or `InputStream` used to reset the status to 200, so a
handler that set 201 answered 200.

**Invalid query parameters get 400.** A `@QueryParam` that cannot be
converted, e.g. `num=abc` for an `int`, used to fail with 500. Malformed
url-encoding in a form gets 400 too.

**404 and 500 responses carry a short `text/plain` body** instead of being
empty, so a client can tell an error from an empty document.

### @QueryParam

**An absent `Integer` is now `null`**; it used to be -1, like an `int`. An
absent `int` is still -1. Code that tests for -1 on an `Integer` must test for
`null`, or declare `defaultValue = "-1"`.

**An empty value counts as absent** for every type but `String`: `?num=` gives
the default rather than failing.

### flak-login

**`@WithPermission` and `@WithAnyPermission` on a class are enforced.** They
used to be silently ignored, which left the routes of the class open. Check
the classes that carry them: their routes now require the permission.

**The login redirect passes the whole URL.** A user sent to the login page
used to get `?url=` followed by the bare path. The query string is now
included and the whole value is url-encoded, e.g.
`/login?url=%2Fdata%3Fx%3D1`. A login page that reads it with `getQuery()` or
`@QueryParam` receives it decoded. A login page whose path already has a
query string gets `&url=`.

**The session cookie is `Secure` over HTTPS**, so that browsers never send
the token in clear.

**Expired sessions are closed** when another session opens, at most once a
minute, through `closeSession()`. They used to stay in memory until a request
presented them again, which could be never.

The `Set-Cookie` header of a session that expires no longer ends with a
stray `;`.

### Routing and responses

**`RouteHandler.getRoute()` is relative to the app.** The JDK backend used
to include the path of the app in it, unlike the Netty one. Both now report
the route as it was declared in `@Route`. Prepend `App.getPath()` for an
absolute path, as `RouteDumper` does.

**With the JDK backend, every request under the path of an app reaches it.**
The embedded HttpServer used to answer requests outside the static prefix of
every route with its own HTML 404. The unknown page handler and the
before-all hooks now see those requests too, and the 404 is Flak's
`text/plain` one, as with Netty.

**`redirect()` keeps URLs as they are.** It used to prefix whatever it was
given with the path of the app, so in an app at `/shop`,
`redirect("https://example.com/")` sent the client to
`/shophttps://example.com/`. A path is still relative to the app.

**An `int` path variable that is not a number gets 404**, e.g. `/items/abc`
for `@Route("/items/:id")`. It used to fail with 500.

**Static resources stay inside the directory they are served from.** A
request that climbs out of it, with `..` or an absolute path, used to be
served whatever it pointed to, class files included when serving from the
classpath. It now gets 404, as does a missing file under `serveDir()`, which
used to fail with 500.

**The root of static resources is served.** `/static/` used to be a 404
for resources served at `/static`, and so was `/` for resources served at
the root. It now serves the `index.html` there, and `/static` redirects to
`/static/`, as does a subdirectory of a served directory requested without
its trailing slash. A route of the app at the same path keeps precedence.

### OpenAPI

Query parameters of type `int` are described as `integer`; they used to be
`int`, which OpenAPI does not define. Every type `@QueryParam` accepts is
described, where the generator used to fail on `String[]`. `@Head` routes are
reported as HEAD instead of GET.

### For backend and plugin authors

- `AppFactory` gained `setPlugins()`.
- `SPRequest` gained `setMaxBodySize()`: an implementation outside the
  project needs it.
- `SPPlugin.install()` was added as a default method. It is now where a
  plugin registers its extractors, called by `App.addPlugin()`.
- `AbstractApp.getMethodHandlers()` and
  `AbstractMethodHandler.processResponse()`/`isApplicable()` are public.
- `AbstractApp.addHandler0()` returns the handler it added.
- A handler can be a fallback (`AbstractMethodHandler.setFallback()`): it
  only serves what no other handler of the same route takes. A backend must
  try the other handlers first.
- The Netty backend no longer logs each request, nor prints its address to
  stdout, unless `-Ddebug=true`.
