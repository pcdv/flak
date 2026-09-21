# Netty backend for Flak

An implementation of the Flak SPI on top of [Netty](https://netty.io/), as an
alternative to `flak-backend-jdk`.

Since 3.0 it is a complete backend: the whole Flak test suite runs against it
(`./gradlew :flak-tests:testNetty`), covering routing, path and query
arguments, forms, JSON, gzip compression, chunked responses, static resources,
authentication and HTTPS.

To use it, replace the JDK backend on the classpath:

```groovy
dependencies {
  implementation "com.github.pcdv.flak:flak-api:3.0"
  runtimeOnly "com.github.pcdv.flak:flak-backend-netty:3.0"
}
```

Application code is unchanged: the backend is discovered with `ServiceLoader`.
If both backends are present, select this one explicitly:

```java
AppFactory factory = Flak.getFactory(cls -> cls.getName().contains("netty"));
```

The cost is Netty itself: `netty-codec-http` and its transitive dependencies
weigh about 3.4MiB, against nothing at all for the JDK backend. Unless Netty
is already part of your stack, `flak-backend-jdk` remains the better default.

## Plugging flak into a netty server you own

If the application already runs its own netty server, or needs to serve
another protocol on the same port, flak can serve the HTTP routes without
owning anything: it binds nothing, starts no event loop, and only contributes
a handler to the pipeline you build.

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

`WebSocketServerProtocolHandler` answers the upgrade on its own path and passes
every other request down the pipeline, where flak routes it as usual. Both
protocols share one port.

The `HttpObjectAggregator` above is what the websocket handshake needs, and it
buffers every request body up to its limit. Flak does not need it: without it,
request bodies are streamed to the route handlers and their size is capped by
`@MaxBodySize` instead. Move it after the websocket handler, or leave it out,
if the application also takes large uploads.

In this mode the application owns the socket, so `setSSLContext()` is rejected:
TLS belongs to the pipeline you build. Stopping the app releases the executor
flak uses to run route handlers, and nothing else.

See
[NettySharedPortTest](https://github.com/pcdv/flak/blob/master/flak-tests/src/test/java/flask/test/NettySharedPortTest.java)
for a complete, working example.

## Limitations

 * async responses (replying from a thread other than the one running the
   handler) are not supported, as with the JDK backend: the response is sent
   as soon as the handler returns.
