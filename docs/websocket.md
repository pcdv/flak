# WebSockets

`flak-websocket` serves websockets on the routes of an app running on the JDK
backend. Its API is modelled on
[Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket), so that code
written for it ports with few changes.

The HttpServer of the JDK has no support for websockets, and its API gives no
way to take over a connection. This add-on gets around that with reflection
(see [How it works](#how-it-works)). It is small and tested, but it relies on
internals of the JDK. With the Netty backend, use Netty's own websocket
handlers instead, see [Backends](backends.md#plugging-flak-into-a-netty-server-you-own).

**This add-on is experimental.** Its API may still change in a minor
release, as it gets used in real applications.

```groovy
dependencies {
  implementation "com.github.pcdv.flak:flak-api:3.0.0"
  implementation "com.github.pcdv.flak:flak-websocket:3.0.0"
  implementation "com.github.pcdv.flak:flak-backend-jdk:3.0.0"
}
```

## A first endpoint

Extend `WebSocketEndpoint`, override the callbacks you need, and hand the
requests of a route over to `accept()`:

```java
public class Chat {
  private final WebSocketEndpoint room = new WebSocketEndpoint() {
    @Override
    public void onMessage(WebSocket conn, String message) {
      broadcast(message);
    }
  };

  @Route("/chat")
  public void chat(Request request) throws IOException {
    room.accept(request);
  }
}
```

A client connects to `ws://localhost:8080/chat`. A runnable version, with its
page, is in [Chat.java](../flak-examples/src/main/java/flak/examples/Chat.java).

`accept()` checks the handshake, answers it, and serves the websocket until
it is closed: it returns after `onClose()`. A request that is not a websocket
handshake gets a 400.

## The handshake is a route

Because the handshake goes through an ordinary route, everything that applies
to routes applies to it: path variables, hooks, and plugins. With
[flak-login](login.md), a websocket can be restricted to logged-in users, and
the user attached to it:

```java
@Route("/notifications")
@LoginRequired
public void notifications(FlakUser user, Request request) throws IOException {
  endpoint.accept(request, user);
}
```

The second argument of `accept()` is attached to the websocket before
`onOpen()` is called. `conn.getAttachment()` returns it in every callback.
It can be anything, e.g. the session, or a path variable:

```java
@Route("/room/:name")
public void room(String name, Request request) throws IOException {
  endpoint.accept(request, name);
}
```

To refuse a websocket, throw an `HttpException` before calling `accept()`.
`onOpen()` also receives the request, e.g. to read its headers or cookies.

## Callbacks and sending

| Callback | Called when |
| -------- | ----------- |
| `onOpen(conn, request)` | the handshake is accepted |
| `onMessage(conn, String)` | a text message arrives |
| `onMessage(conn, ByteBuffer)` | a binary message arrives |
| `onClose(conn, code, reason, remote)` | the websocket is closed, cleanly or not |
| `onError(conn, exception)` | another callback threw, the websocket stays open. Logs by default |

A `WebSocket` sends messages with `send(String)`, `send(byte[])` or
`send(ByteBuffer)`, and pings with `sendPing()`. It is closed with `close()`,
`close(code)` or `close(code, reason)`, using a code of `CloseFrame` or one of
your own between 3000 and 4999. The endpoint lists its websockets with
`getConnections()`, and sends a message to all of them with `broadcast()`.

Messages are received whole: fragments are reassembled, and pings answered.
A message bigger than `setMaxMessageSize()`, 16MiB by default, closes the
websocket with `CloseFrame.TOOBIG` (1009).

## Lost connections

A client can vanish without closing its connection, e.g. a laptop going to
sleep or a network going down. Nothing tells the server, which could keep
the thread of its websocket for hours. Worse, once the client's buffers are
full, a send to it blocks: a `broadcast()` would stall every other client
along with it.

So while websockets are open, a watchdog checks them, as Java-WebSocket
does. With the default timeout of 60 seconds:

- a websocket from which nothing has arrived for 30 seconds is pinged.
  Browsers and client libraries answer pings on their own.
- a websocket that has still sent nothing after 90 seconds is dropped.
- so is one that has taken none of what it is sent for 60 seconds: the send
  stuck on it throws `UncheckedIOException`, and the broadcast moves on.

A dropped websocket is closed with `CloseFrame.ABNORMAL_CLOSE` (1006), and a
reason starting with "Connection lost". Change the timeout with
`setConnectionLostTimeout(seconds)`, or disable the watchdog with 0. It runs
on two threads of its own, only while the endpoint has open websockets.

## Subprotocols

A client may ask for a subprotocol, e.g. `new WebSocket(url, "v1.chat")` in a
browser. Some clients, e.g. those of GraphQL or STOMP, refuse a server that
does not agree to one. List those the endpoint speaks:

```java
endpoint.setProtocols("v2.chat", "v1.chat");
```

The handshake agrees on the first one the client offers among them, which
`conn.getProtocol()` returns. A client that offers none of them is accepted
without a subprotocol, and `getProtocol()` returns null. A browser that asked
for one then gives up, as the standard has it. To refuse such a client with a
clear status instead, check `request.getHeader("Sec-WebSocket-Protocol")` in
the route.

## Threads

Each websocket keeps the thread that serves its route until it is closed. All
its callbacks run on that thread, one at a time. The default executor of the
JDK backend creates threads as needed, so this costs a thread per open
websocket. Keep that in mind before bounding the executor (see
[Apps and servers](apps-and-servers.md#threads)): each open websocket takes
one of its threads.

On JDK 24 and later, an executor of virtual threads makes this cheap:

```java
factory.getServer().setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

Do not use virtual threads on JDK 21 to 23. All virtual threads run on a
small pool of ordinary threads, one per CPU core by default. A virtual
thread normally gives its ordinary thread back while it waits. On these JDKs
it cannot do that inside a `synchronized` method, and HttpServer reads from
the network in `synchronized` methods. So each websocket waiting for a
message holds one thread of the pool. Once as many websockets as there are
cores are waiting, the pool is used up, and the whole server stops
responding.

`send()` and `close()` can be called from any thread. A send blocks until its
frame is handed to the socket, which takes longer with a slow client (see
[Lost connections](#lost-connections) for one that stops reading). A send on a
websocket that is closing or closed throws `IllegalStateException`. A send on
a broken connection throws `UncheckedIOException`, and drops the connection.

Stopping the app drops the websockets that are still open: their `onClose()`
gets `CloseFrame.ABNORMAL_CLOSE` (1006). Close them first, e.g. with
`CloseFrame.GOING_AWAY`, to let clients know.

## The JVM flag

Run the JVM with:

```
--add-opens jdk.httpserver/sun.net.httpserver=ALL-UNNAMED
```

Without it, the add-on falls back on `sun.misc.Unsafe`. That works with no
flag at all, but JDK 24 and later print a warning the first time it is used
(still only a warning on JDK 27), and a future JDK will refuse it.

## Limits

- No extensions: messages are never compressed (`permessage-deflate`).
- Without the watchdog, i.e. with `setConnectionLostTimeout(0)`, nothing
  drops a client that vanishes, or that never answers `close()`.
- The JDK backend only. Tested with the HttpServer of JDK 17, 21, 24 and 27,
  over HTTP and HTTPS.

## How it works

HttpServer hands each exchange to a handler on a thread of its own, in
blocking mode. It does not touch the connection again until the exchange is
over, and the exchange is only over once the handler closes it. But it
refuses to send a 1xx status without ending the exchange: after a 101, it
would read the frames of the client as the next HTTP request.

So `accept()` never asks HttpServer to send anything. It reads the two
streams HttpServer reads the request from and writes the response to, from
the fields `ris` and `ros` of `sun.net.httpserver.ExchangeImpl`, and writes
the 101 on them itself. It then reads frames on the thread of the route for
as long as the websocket is open. Going through those streams rather than
the socket keeps whatever the client sent after the handshake, and keeps TLS
working.

To drop a connection, e.g. a lost one, the add-on closes the socket itself,
taken from the field `chan` of the connection of the exchange. Closing the
streams would block on HTTPS, behind the very send it is meant to unblock.

Once the websocket is closed, `accept()` closes the exchange. Since no
response was started, HttpServer closes the connection. The route returns,
flak tries to send its response on the closed connection, and the resulting
`IOException` makes HttpServer drop the connection from its bookkeeping.
