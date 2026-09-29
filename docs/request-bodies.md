# Request bodies

## Reading the body

The body of a request reaches the handler as a stream. It is never held in
memory as a whole, so a handler can pipe an upload of any size straight to
its destination:

```java
@Route("/api/import")
@Post
@MaxBodySize(MaxBodySize.UNLIMITED)
public void upload(Request r) throws IOException {
  try (OutputStream out = Files.newOutputStream(target)) {
    r.getInputStream().transferTo(out);
  }
}
```

The body can be read in several ways, depending on what it holds:

- `request.getInputStream()`: the raw bytes
- a `Form` argument: an url-encoded form (see [Handler arguments](arguments.md#forms))
- an argument parsed by an input format, e.g. with [`@JSON`](json.md)

The body can only be read once, whichever way is used. A handler that needs
both the form and the raw bytes must read the bytes and parse them itself.

## Size limit

A handler that loads the body into memory should not be at the mercy of its
client, so bodies are limited to **16MiB** by default. A request over the
limit is rejected with **413**:

- before anything is read, when its `Content-Length` announces more
- as soon as the limit is crossed otherwise, e.g. for a chunked upload

The limit can be changed at three levels, from the broadest to the most
specific:

| Scope | How |
| ----- | --- |
| the app | `app.setMaxBodySize(bytes)` |
| a class of handlers | `@MaxBodySize(bytes)` on the class |
| a handler | `@MaxBodySize(bytes)` on the method, or at runtime: `app.getHandler("POST", "/api/import").setMaxBodySize(bytes)` |

The most specific one applies, and for a handler, a limit set at runtime
wins over the annotation. `MaxBodySize.UNLIMITED` removes the limit, for
handlers that stream the body somewhere rather than keep it in memory.

An annotation can only hardcode a value. To let the users of an application
configure the limit of an endpoint, set it on the handler from the
application's own settings:

```java
app.getHandler("POST", "/api/import").setMaxBodySize(settings.getMaxUpload());
```

`getHandler()` fails if no handler is bound to the method and route, so a typo
in a configured route is reported rather than ignored. `app.getHandlers()`
returns all of them, to configure them in bulk.

### Escape hatch

When legitimate clients get 413 in production and the application has no
setting of its own for the limit, start the JVM with the system property
`flak.maxBodySize`:

```
-Dflak.maxBodySize=2g    # every limit below 2GiB becomes 2GiB
-Dflak.maxBodySize=-1    # no limit at all
```

It raises every limit below it, whether it comes from the app, an annotation
or a handler, and never lowers one. The value is a number of bytes,
optionally followed by `k`, `m` or `g` (powers of 1024). It is read on every
request, so it can also be changed in a running JVM. An invalid value is
ignored, with a message on the standard error.
