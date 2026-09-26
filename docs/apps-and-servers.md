# Apps and servers

An **app** is a set of route handlers served under a path. A **web server**
listens on a port and can host several apps. Both come from an
[AppFactory](../flak-api/src/main/java/flak/AppFactory.java), which the
backend on the classpath provides (see [Backends](backends.md)).

## Creating an app

```java
AppFactory factory = Flak.getFactory();
factory.setPort(8080);

App app = factory.createApp();
app.scan(new MyRoutes());
app.start();
```

`Flak.createHttpApp(8080)` does the first three lines in one call.

`scan()` registers the public methods of an object that carry `@Route`,
inherited ones included. It returns the app, so calls can be chained. The
object can be of any class, including an anonymous one. Routes can be added
before or after `start()`. [Routing](routing.md) covers routes in detail.

## Port and address

The port and the address must be set before the first app starts.

- `factory.setPort(8080)` listens on every interface.
- `factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 8080))`
  listens on one address only, here the loopback.
- Port 0 picks a free port. Read it back once the server has started with
  `app.getServer().getPort()`, or `factory.getPort()`.

## Several apps on one server

The apps created by one factory share its web server, provided each one has
its own path:

```java
AppFactory factory = Flak.getFactory();
factory.setPort(8080);

App admin = factory.createApp("/admin");
admin.scan(new AdminRoutes());
admin.start();

App shop = factory.createApp("/shop");
shop.scan(new ShopRoutes());
shop.start();
```

A route is always relative to its app. `@Route("/orders")` in `shop` answers
`/shop/orders`, and inside the handler `request.getPath()` is `/orders`.

Each app has its own plugins, error handlers and settings. With
[flak-login](login.md), each app also has its own session cookie, whose path is
the path of the app.

These methods deal with the path of an app:

| Method | Returns, for an app at `/shop` on port 8080 |
| ------ | ------------------------------------------- |
| `app.getPath()` | `/shop`, or an empty string for an app at the root |
| `app.getRootUrl()` | `http://localhost:8080/shop` |
| `app.absolutePath("/orders")` | `/shop/orders` |

## Starting and stopping

`app.start()` starts the web server if it is not running yet. The server can
also be started first, with `factory.getServer().start()`, and apps added to it
afterwards.

`app.stop()` removes the app from the server. The server stops with the last
app. `factory.getServer().stop()` stops it at once, whatever the number of
apps.

## HTTPS

Build a `javax.net.ssl.SSLContext` and give it to the server before starting it:

```java
char[] password = "secret".toCharArray();
KeyStore keyStore = KeyStore.getInstance("PKCS12");
try (InputStream in = new FileInputStream("server.p12")) {
  keyStore.load(in, password);
}
KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
kmf.init(keyStore, password);

SSLContext ssl = SSLContext.getInstance("TLS");
ssl.init(kmf.getKeyManagers(), null, null);

AppFactory factory = Flak.getFactory();
factory.getServer().setSSLContext(ssl);
```

The server then speaks HTTPS only, and `app.getRootUrl()` starts with
`https://`. Both backends support this.

## Host name

`getRootUrl()` uses the host name of the server, `localhost` by default. If
the URLs an app builds are shown to users on other machines, set the name
they should use:

```java
factory.getServer().setHostName("orders.example.com");
```

This changes neither the address the server listens on nor how requests are
matched.

## Threads

Each request is handled on a thread taken from an executor. By default this
is a cached thread pool, which grows with the number of concurrent requests.
To bound it, give the server your own executor before it starts:

```java
factory.getServer().setExecutor(Executors.newFixedThreadPool(32));
```

A handler may block for as long as it needs to. It holds its thread until it
returns, and the response is sent at that point, so a handler cannot hand the
response over to another thread and reply later.

While a handler runs, `app.getRequest()` and `app.getResponse()` return the
request and response of the current thread. Code the handler calls can use
them without having them passed along.
