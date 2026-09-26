# Static resources

An app can serve files, from a directory or from the classpath (e.g. the web
front end packaged in the application's jar). No extra dependency is needed.

```java
// from the classpath: src/main/resources/webapp/... in the jar
app.serveClasspath("/ui", "/webapp");

// from a directory
app.serveDir("/downloads", new File("/var/data/downloads"));
```

The first argument is the path under which the files are served, relative to
the app. Both methods return the app, so calls can be chained.

`serveDir()` accepts a directory that does not exist yet, e.g. a cache
created on demand: its files are served once they are there. It fails if the
file exists but is not a directory.

## What is served

- `/ui/css/main.css` gives `webapp/css/main.css`, with a query string ignored.
- A path ending with `/` gives the `index.html` of that directory: `/ui/`
  gives `webapp/index.html`, and `/ui/docs/` gives `webapp/docs/index.html`.
- `/ui` is redirected to `/ui/`, so that the relative links of the page
  resolve against the right directory. With a directory served from the file
  system, so is any subdirectory requested without its trailing slash.
- A missing file gives a 404.
- A path that climbs out of the served directory, with `..` or an absolute
  path, gives a 404.

## Options

A [ResourceOptions](../flak-api/src/main/java/flak/ResourceOptions.java)
argument changes how files are served:

```java
app.serveClasspath("/ui", "/webapp", new ResourceOptions()
  .restricted()
  .classLoader(MyApp.class.getClassLoader())
  .contentTypes(myContentTypes));
```

| Option | Effect |
| ------ | ------ |
| `restricted()` | only logged-in users get the files, see below |
| `classLoader(loader)` | the class loader to look classpath resources up with |
| `contentTypes(provider)` | decides the content type of each file, and whether it is compressed |

### Content types

The `Content-Type` of a file is chosen from its extension, in any case,
among the common web types (HTML, CSS, JavaScript, JSON, images, fonts,
archives…). A file with an unknown extension is served without one. To
change the mapping, pass your own
[ContentTypeProvider](../flak-api/src/main/java/flak/ContentTypeProvider.java):

```java
ContentTypeProvider defaults = new DefaultContentTypeProvider();
app.serveClasspath("/ui", "/webapp", new ResourceOptions().contentTypes(path ->
  path.endsWith(".wasm") ? "application/wasm" : defaults.getContentType(path)));
```

Text, JSON and JavaScript are [compressed](compression.md) automatically.
Override `shouldCompress()` in the provider to change that.

### Restricting access

With [flak-login](login.md), files can be restricted to logged-in users:

```java
app.serveDir("/reports", reportsDir, new ResourceOptions().restricted());
```

A user who is not logged in is redirected to the login page, which can
itself be a static file served from a public path. Without flak-login,
nothing would keep anyone away from the files, so serving them fails
instead.

### Class loader

By default, classpath resources are looked up with the class loader of Flak.
If they are not visible to it, e.g. in an application server or a plugin
system with its own class loaders, pass the one to use with
`classLoader()`.

## Serving at the root

`serveClasspath("/", ...)` serves files at the root of the app, and its
`index.html` at `/`. The routes of the app still take precedence, including
a route at `/`, whether it was scanned before or after. But a URL that
matches neither a route nor a file gets the 404 of the resources, so it
never reaches the [unknown page handler](errors-and-hooks.md#unknown-urls).
Prefer a dedicated path, such as `/ui` or `/static`, when you can.
