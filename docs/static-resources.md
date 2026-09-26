# Static resources

`flak-resource` serves files, from a directory or from the classpath (e.g.
the web front end packaged in the application's jar).

```groovy
implementation "com.github.pcdv.flak:flak-resource:3.0"
```

It is not a plugin: create it on the app that serves the files.

```java
FlakResourceImpl resources = new FlakResourceImpl(app);

// from the classpath: src/main/resources/webapp/... in the jar
resources.servePath("/ui", "/webapp");

// from a directory
resources.serveDir("/downloads", new File("/var/data/downloads"));
```

`servePath(url, path)` serves from a directory if `path` is an existing
directory, and from the classpath otherwise. `serveDir(url, dir)` serves from
a directory only.

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

## Content types

The `Content-Type` of a file is chosen from its extension, in any case,
among the common web types (HTML, CSS, JavaScript, JSON, images, fonts,
archives…). A file with an unknown extension is served without one. To
change the mapping, give the resources your own
[ContentTypeProvider](../flak-resource/src/main/java/flak/plugin/resource/ContentTypeProvider.java):

```java
ContentTypeProvider defaults = new DefaultContentTypeProvider();
resources.setContentTypeProvider(path ->
  path.endsWith(".wasm") ? "application/wasm" : defaults.getContentType(path));
```

Text, JSON and JavaScript are [compressed](compression.md) automatically.
`shouldCompress()` changes that.

## Restricting access

With [flak-login](login.md), resources can be restricted to logged-in users:

```java
resources.servePath("/ui", "/webapp", null, true);
resources.serveDir("/downloads", dir, true);
```

A user who is not logged in is redirected to the login page, which can
itself be a static file served from a public path.

## Class loader

By default, classpath resources are looked up with the class loader of
`flak-resource`. If the resources are not visible to it, e.g. in an
application server or a plugin system with its own class loaders, pass the
one to use:

```java
resources.servePath("/ui", "/webapp", MyApp.class.getClassLoader(), false);
```

## Serving at the root

`servePath("/", ...)` serves files at the root of the app, and its
`index.html` at `/`. The routes of the app still take precedence, including
a route at `/`, whether it was scanned before or after. But a URL that
matches neither a route nor a file
now gets the 404 of the resource handler, so it no longer reaches the
[unknown page handler](errors-and-hooks.md#unknown-urls). Prefer a dedicated
path, such as `/ui` or `/static`, when you can.
