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

## Limitations

 * async responses (replying from a thread other than the one running the
   handler) are not supported, as with the JDK backend: the response is sent
   as soon as the handler returns.
