# Errors and hooks

## How a request is served

1. The hooks registered with `addBeforeAllHook()` run.
2. The request is matched against the routes. If none matches, the unknown
   page handler serves it, or it gets a 404.
3. The hooks of the handler run. This is where [flak-login](login.md)
   checks sessions and permissions.
4. The arguments are extracted: path variables, query parameters, body…
5. The handler runs. The success handlers are then notified.
6. What the handler returned is written into the response.

An `HttpException` thrown at any of these steps sets the status of the
response. Any other exception gives a 500 and is passed to the error
handlers.

## Answering with an error status

Throw an [HttpException](../flak-api/src/main/java/flak/HttpException.java)
with the status and a message:

```java
@Route("/items/:id")
public String item(String id) {
  Item item = items.get(id);
  if (item == null)
    throw new HttpException(404, "No such item: " + id);
  return item.toString();
}
```

The client receives that status, with the message as a `text/plain` body. An
`HttpException` is a deliberate answer rather than a failure, so it is not
reported to the error handlers.

Flak itself answers with:

| Status | When |
| ------ | ---- |
| 400 | a `@QueryParam` or a form that cannot be parsed |
| 401 | a user who is not logged in, when a route requires login and no login page is set ([flak-login](login.md)) |
| 403 | a logged-in user who lacks a permission ([flak-login](login.md)) |
| 404 | a URL that matches no route |
| 413 | a body over the [size limit](request-bodies.md#size-limit) |
| 500 | a handler failing with any other exception |

The 404 and 500 responses carry a short `text/plain` body, "Not found" and
"Internal Server Error", so a client can tell an error from an empty
document.

## Handler failures

When a handler throws anything but an `HttpException`, the request is
answered with 500. The error handlers are notified; if there are none, the
exception is logged.

```java
app.addErrorHandler((status, request, error) ->
  log.error("{} {} failed", request.getMethod(), request.getPath(), error));
```

An error handler may also write its own response, e.g. a JSON error document:

```java
app.addErrorHandler((status, request, error) -> {
  Response r = request.getResponse();
  r.addHeader("Content-Type", "application/json");
  try {
    r.getOutputStream().write(toJson(error));
  }
  catch (IOException e) {
    // the client is gone
  }
});
```

Setting a status other than 500 is allowed too.

The client is not told what went wrong, since that could leak internal
details. When debugging, start the JVM with `-Ddebug=true`: the stack trace
is then sent as the body of the 500, and Flak logs more. Never enable it in
production.

## Unknown URLs

A request that matches no route of the app gets a 404 with "Not found". To
serve such requests yourself, e.g. with a custom page or for a single-page
application that routes on the client side:

```java
app.setUnknownPageHandler(req -> {
  Response r = req.getResponse();
  r.setStatus(404);
  r.addHeader("Content-Type", "text/html");
  r.getOutputStream().write(notFoundPage);
});
```

Error handlers are not called for unknown URLs.

## Success handlers

A success handler is notified after each handler that returns normally, with
the arguments it received and the value it returned. It's a convenient place
for auditing or metrics:

```java
app.addSuccessHandler((request, method, args, result) ->
  audit.log(currentUser(), method.getName(), args));
```

It runs before the response is written.

## Hooks before every request

A hook added with `addBeforeAllHook()` runs before each request, including
requests for unknown URLs. Adding one is part of the service provider
interface, so it requires a cast:

```java
((AbstractApp) app).addBeforeAllHook(req -> {
  req.getResponse().addHeader("X-Frame-Options", "DENY");
});
```

To reject a request, a hook writes the response (status, body) and throws
`BeforeHook.STOP`. Nothing else then runs for that request.

Hooks that apply to some handlers only, e.g. depending on their annotations,
are what [plugins](plugins.md#writing-a-plugin) are for.

## Logging

Flak writes its few messages (warnings, errors) to `System.err`, without a
logging library. `-Ddebug=true` adds debug messages.
