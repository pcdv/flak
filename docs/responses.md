# Responses

## Return values

What a handler returns becomes the body of the response:

| Return type | Response |
| ----------- | -------- |
| `String` | the string, encoded in UTF-8 |
| `byte[]` | the bytes |
| `InputStream` | the contents of the stream, which is closed afterwards |
| `void`, or `null` | an empty body |
| `Response` | nothing more: the handler has already set the status and written the body |
| any other type | converted by an [output formatter](#output-formatters), e.g. to JSON |

The status is 200 unless the handler set another one.

Flak does not guess the content type of a `String`, `byte[]` or
`InputStream`. Set it when the client needs it:

```java
@Route("/report.csv")
public String report(Response r) {
  r.addHeader("Content-Type", "text/csv");
  return buildCsv();
}
```

An `InputStream` suits large content: it is copied to the client as it is
read, and never held in memory as a whole. If reading it fails midway, the
connection is aborted, so the client sees an error rather than a truncated
document that looks complete.

## Output formatters

A handler can return any object, provided a formatter converts it. JSON is
the common case, and [flak-jackson](json.md) needs no more than `@JSON`.
Other formats are registered under a name, which handlers refer to with
`@OutputFormat`:

```java
app.addOutputFormatter("CSV", (data, resp) -> {
  resp.addHeader("Content-Type", "text/csv");
  Csv.write((List<?>) data, resp.getOutputStream());
});

@Route("/api/items.csv")
@OutputFormat("CSV")
public List<Item> items() { ... }
```

Register formatters before scanning the handlers that use them. A handler
returning an unknown type without a formatter makes `scan()` fail with "No
@OutputFormat or @JSON around method …".

## Status and headers

Take a `Response` parameter, or call `app.getResponse()`:

```java
@Route("/items")
@Post
public String create(Form form, Response r) {
  Item item = save(form);
  r.setStatus(201);
  r.addHeader("Location", app.absolutePath("/items/" + item.getId()));
  return item.getId();
}
```

- `addHeader()` adds a header, and can be called several times for the same
  name (e.g. `Set-Cookie`). `hasResponseHeader(name)` tells whether one is
  set.
- `setStatus()` sets the status, and `isStatusSet()` tells whether one has
  been set.

The status and the headers go out with the first bytes of the body. Set them
before writing a large body or flushing the output stream. After that point
they can no longer change.

To reply with an error status and a message, throw an `HttpException` (see
[Errors and hooks](errors-and-hooks.md)).

## Redirects

```java
@Route("/old")
public void old(Response r) {
  r.redirect("/new");
}
```

This answers 302 with a `Location` header. The path is relative to the app:
in an app at `/shop`, `redirect("/new")` sends the client to `/shop/new`. To
send it to another site, set the header yourself:

```java
r.addHeader("Location", "https://example.com/");
r.setStatus(302);
```

## Writing the body yourself

A handler can write into `response.getOutputStream()` instead of returning a
value. Return `void`, or return the `Response` if you prefer to make it
explicit. There is no need to close the stream: Flak finishes the response
when the handler returns.

Calling `flush()` sends what has been written so far, as a chunk. This keeps
the connection open for as long as the handler runs, so the handler can
stream progress, logs or events:

```java
@Route("/events")
public void events(Response r) throws Exception {
  r.addHeader("Content-Type", "text/event-stream");
  r.addHeader("Cache-Control", "no-cache");
  OutputStream out = r.getOutputStream();
  while (running) {
    out.write(("data: " + nextEvent() + "\n\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }
}
```

This is how the Server-Sent Events
[example](../flak-examples/src/main/java/flak/util/sse/SSEExample.java) works.
Each open stream holds a thread for as long as it lasts, so
[size the executor](apps-and-servers.md#threads) accordingly.

## HEAD

A GET handler does not answer HEAD requests. A path that should answer them
needs a handler with `@Head`, which sets the status and headers. The body is
not sent anyway:

```java
@Route("/files/*path")
@Head
public Response exists(String path, Response r) {
  r.setStatus(Files.exists(root.resolve(path)) ? 200 : 404);
  return r;
}
```

## Replying later

Neither backend supports asynchronous responses. The response is complete
as soon as the handler returns, so a handler cannot hand it over to another
thread and reply from there. A handler that needs to wait for something must
block until it is available.
