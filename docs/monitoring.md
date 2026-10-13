# Monitoring

*Since Flak 3.4.0.*

## Request listeners

A `RequestListener` is notified at the beginning and at the end of every
request, on the thread that serves it:

```java
app.addRequestListener(new RequestListener() {
  @Override
  public void onBegin(Request request) {
    // the route is known, the handler has not run yet
  }

  @Override
  public void onEnd(Request request, RequestSummary summary) {
    stats.add(summary.getRoute(), summary.getStatus(),
              summary.getDurationNanos(), summary.getBytesOut());
  }
});
```

- `onBegin()` is called once the request matched a route, before the hooks
  of the handler (so before the login check) and before the handler. A
  request that matches no route, or that a hook added with
  `addBeforeAllHook()` rejects, does not begin: hooks before every request
  run before routing.
- `onEnd()` is called for every request, matched or not, once the response
  is finished. `request.getRoute()` and `summary.getRoute()` are then null
  if no route matched.

The [summary](../flak-api/src/main/java/flak/RequestSummary.java) holds:

| Method               | Content                                                     |
|----------------------|-------------------------------------------------------------|
| `getRoute()`         | template of the route, e.g. `/user/:id`, or null            |
| `getStatus()`        | status of the response                                      |
| `getBytesIn()`       | bytes of the request body read, by the handler or by Flak   |
| `getBytesOut()`      | bytes of the response body sent, after compression          |
| `getStartNanos()`    | when Flak started serving the request (`System.nanoTime()`) |
| `getDurationNanos()` | time to serve it, up to the end of the response             |

A listener must be quick: it runs on the serving thread, and the duration of
`onBegin()` counts in the duration of the request. An exception thrown by a
listener is logged and does not affect the request.
`removeRequestListener()` removes a listener.

## JFR event

Each request is also recorded as a Java Flight Recorder event named
`flak.Request`, with the fields of the summary plus the app, the HTTP method
and the path (without the query string). It is committed by the thread that
served the request, so its start time, duration and thread tell which
execution samples of a recording belong to the request: a profiler can show
where the time of each route goes, with no annotation.

The event is disabled by default, so that a recording made with the settings
of the JDK does not get one event per request. Enable it in a recording:

```java
recording.enable("flak.Request");
```

or in a `.jfc` file given to `-XX:StartFlightRecording`:

```xml
<event name="flak.Request">
  <setting name="enabled">true</setting>
</event>
```

Its name and fields are a contract, which other HTTP stacks can follow to be
understood by the same tools:
[RequestEvent](../flak-spi/src/main/java/flak/spi/RequestEvent.java).
