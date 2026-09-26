# Flak documentation

The [README](../README.md) shows how to add Flak to a build and write a first
app. These pages cover each feature in detail.

**Building an app**

- [Apps and servers](apps-and-servers.md): creating apps, port and address,
  hosting several apps on one server, HTTPS, threads
- [Routing](routing.md): `@Route`, HTTP methods, path variables, splats,
  matching rules, listing the routes of an app
- [Handler arguments](arguments.md): path variables, query parameters, forms,
  the request, custom arguments
- [Responses](responses.md): return types, formatters, status and headers,
  redirects, streaming
- [Request bodies](request-bodies.md): streaming uploads, size limits
- [Errors and hooks](errors-and-hooks.md): error statuses, error and success
  handlers, unknown URLs, hooks that run before every request
- [Compression](compression.md): gzip
- [Static resources](static-resources.md): serving files from a directory
  or the classpath

**Add-ons**

- [Plugins](plugins.md): how add-ons are installed, and how to write one
- [JSON](json.md): `flak-jackson`
- [Authentication](login.md): `flak-login`, sessions and permissions
- [OpenAPI](openapi.md): `flak-swagger`, generating a specification

**Running**

- [Backends](backends.md): the JDK HTTP server or Netty, and plugging Flak
  into a Netty server you own
- [Migrating to 3.0](migration-3.0.md)

Runnable examples are in
[flak-examples](../flak-examples/src/main/java/flak), and the
[tests](../flak-tests/src/test/java/flask/test) cover every feature. The test
suite runs against both backends.
