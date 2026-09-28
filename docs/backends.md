# Backends

Route handling does not depend on the HTTP server underneath. Two backends
are available, and the same test suite runs against both:

| Backend | Extra dependencies | Description |
| ------- | ------------------ | ----------- |
| `flak-backend-jdk` | none | The [HttpServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html) included in the JDK. The default choice, and the lightest by far. |
| `flak-backend-netty` | ~3.4MiB | [Netty](https://netty.io/), useful if it is already part of your stack, or to share a port with websockets. |

## Choosing a backend

A backend is discovered on the classpath with `ServiceLoader`, so switching
from one to the other only means changing the `runtimeOnly` dependency.
Application code is unchanged:

```groovy
dependencies {
  implementation "com.github.pcdv.flak:flak-api:3.0"
  runtimeOnly "com.github.pcdv.flak:flak-backend-netty:3.0"
}
```

If both are on the classpath, `Flak.getFactory()` returns whichever comes
first. Pass a predicate to pick one explicitly:

```java
AppFactory factory = Flak.getFactory(cls -> cls.getName().contains("netty"));
```

Both backends support everything described in these pages, HTTPS included.
Neither supports asynchronous responses: the response is sent as soon as the
route handler returns.

## Plugging Flak into a Netty server you own

If the application already runs its own Netty server, or needs to serve
another protocol on the same port, Flak can serve the HTTP routes without
owning anything. It binds nothing, starts no event loop, and only
contributes a handler to the pipeline you build:

```java
NettyAppFactory factory = NettyAppFactory.attached(null, false);
App app = factory.createApp();
app.scan(new MyRoutes());

bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
  protected void initChannel(SocketChannel ch) {
    ch.pipeline()
      .addLast(new HttpServerCodec())
      .addLast(new HttpObjectAggregator(65536))            // for the ws handshake
      .addLast(new WebSocketServerProtocolHandler("/ws"))  // your websockets
      .addLast(new MyFrameHandler())
      .addLast("flak", factory.getServer().getHttpHandler());
  }
});

Channel channel = bootstrap.bind(0).sync().channel();

// so that App.getRootUrl() knows what to advertise
factory.setLocalAddress((InetSocketAddress) channel.localAddress());
app.start();
```

`WebSocketServerProtocolHandler` answers the upgrade on its own path and
passes every other request down the pipeline, where Flak routes it as usual.
Both protocols share one port.

The `HttpObjectAggregator` above is needed for the websocket handshake, and
it buffers every request body up to its limit. Flak does not need it: without
it, request bodies are streamed to the route handlers, and their size is
capped by [`@MaxBodySize`](request-bodies.md#size-limit) instead. If the
application also accepts large uploads, move the aggregator after the
websocket handler, or leave it out.

In this mode the application owns the socket, so `setSSLContext()` is
rejected: TLS belongs to the pipeline you build. Stopping the app releases
the executor Flak uses to run route handlers, and nothing else.

[NettySharedPortTest](../flak-tests/src/test/java/flask/test/NettySharedPortTest.java)
is a complete, working example.

## Writing a backend

A backend implements the service provider interface of `flak-spi`
(`AbstractAppFactory`, `AbstractApp`, `AbstractMethodHandler`, `SPRequest`,
`SPResponse`), and provides a `flak.FlakBackendLoader` listed in
`META-INF/services`. The two existing backends are the best reference.
Running `flak-tests` against a new backend (see its `build.gradle`) shows
what is missing.
