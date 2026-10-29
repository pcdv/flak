# Server-Sent Events

*Since Flak 3.5.0, in flak-util.*

Server-Sent Events push a stream of events from the server to a page, over
a plain HTTP response that stays open. The browser reads it with
`EventSource`, and reconnects by itself when the stream is lost. It is
simpler than a [websocket](websocket.md) when only the server talks, and it
works with both backends.

```groovy
implementation "com.github.pcdv.flak:flak-util:3.6.0"
```

## Sending the same events to every client

`SseBroadcaster` keeps track of the connected clients:

```java
SseBroadcaster prices = new SseBroadcaster();

@Route("/prices")
public void prices(Request req) {
  prices.serve(req);
}

// from any thread
prices.publish(SseEvent.data(json).name("price").id("42"));
```

```js
const source = new EventSource("/prices");
source.addEventListener("price", e => show(JSON.parse(e.data)));
```

`serve()` blocks the handler for as long as the client stays connected. It
returns when the client goes away or when `close()` is called. `publish()`
does not wait: each client has a queue, emptied by the thread that serves it.

| Method | Default |
| ------ | ------- |
| `keepAlive(Duration)` | 15 seconds. A comment sent when nothing was published, see below. Zero disables it |
| `maxQueuedEvents(int)` | 1000. A client that falls further behind is dropped |
| `close()` | ends every stream, and answers 204 to new connections, which tells browsers to stop reconnecting |
| `getClientCount()` | the number of clients connected |

**Disconnected clients.** Nothing tells the server that a client is gone,
until it writes to its connection and the write fails. The keepalive comment
is what frees the thread of a client that left while nothing was published.
It also keeps proxies from closing a quiet connection.

**Slow clients.** Rather than queue every event for a client that does not
read them, `SseBroadcaster` drops it: it stops queueing events for it, and
ends its stream once the write in progress completes. A browser then
reconnects, and can resume from the last event it received. A client that
stops reading altogether, but keeps the connection open, still holds its
thread until the connection breaks: neither backend has a write timeout.

## Event ids and reconnection

A browser that reconnects sends the id of the last event it received in the
`Last-Event-ID` header. A listener passed to `serve()` can send what the
client missed, before the events published from then on:

```java
@Route("/prices")
public void prices(Request req) {
  prices.serve(req, stream -> {
    String last = stream.getLastEventId();
    if (last != null)
      for (Price p : history.since(last))
        stream.send(toEvent(p));
  });
}
```

`retry(Duration)` on an event sets how long the browser waits before
reconnecting, a few seconds by default.

## One stream per handler

`SseStream` is the stream of a single client, for a handler that produces
its own events, e.g. the progress of a job:

```java
@Route("/jobs/:id/progress")
public void progress(String id, Request req) throws Exception {
  SseStream stream = SseStream.open(req);
  for (Step step : jobs.get(id).steps())
    stream.send(SseEvent.data(step.describe()).name("step"));
}
```

The stream ends when the handler returns, and `send()` throws an
`IOException` once the client is gone.

## Events

`SseEvent.data(text)` creates an event, which `name()`, `id()` and
`retry()` complete. Data on several lines is sent as several `data:` lines,
which the browser joins back with `\n`. An event without a name is received
by `EventSource.onmessage`.

## Things to know

- Each connected client holds a thread of the app, so
  [size the executor](apps-and-servers.md#threads) for the number of clients
  expected.
- The events are not compressed, even with `@Compress`: Flak does not
  compress what a handler writes into the output stream, see
  [Compression](compression.md).
- A [request listener](monitoring.md) sees each stream as one request,
  which lasts for as long as the client stays connected.
- Hooks, [authentication](login.md) and [CORS](cors.md) apply as for any
  route: `new EventSource(url, {withCredentials: true})` sends cookies to
  another origin.
- A reverse proxy may buffer the response, holding the events back. With
  nginx, add the `X-Accel-Buffering: no` header, or turn off
  `proxy_buffering` for the route.

A runnable example is in
[flak-examples](../flak-examples/src/main/java/flak/util/sse/SSEExample.java).
