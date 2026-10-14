# Cross-site request forgery

*Since Flak 3.3.0, in flak-util.*

A page of another site can make the browser of a user send requests to the
app: a form posted by script, or a `fetch()` with `mode: "no-cors"`. The
browser adds the cookies of the app, so the request runs with the session of
the user. That is cross-site request forgery (CSRF). The page cannot read
the response, but the harm is done if the request changes something.

```groovy
implementation "com.github.pcdv.flak:flak-util:3.4.1"
```

`CrossOriginProtection` is a [hook that runs before every request](errors-and-hooks.md#hooks-before-every-request).
It refuses with 403 the requests that other origins send, except with GET,
HEAD and OPTIONS:

```java
app.addBeforeAllHook(new CrossOriginProtection());
```

Handlers must then change nothing on GET, which they should not do anyway.

## Why the cookie is not enough

The session cookie of [flak-login](login.md) is `SameSite=Strict` by default,
so browsers do not send it with the requests of another site. This leaves
gaps:

- A *site* is a domain, not a host: the pages of `evil.example.com` are of
  the same site as `app.example.com`, and the cookie goes with their
  requests.
- A cookie set to `SameSite=None`, for a front end on another site, goes
  with every request.
- Without a preflight request, a page can post a form, urlencoded as
  `@FormParams` reads it, or a body of type `text/plain`, which `@JSON`
  handlers parse as JSON whatever its type.

## How requests are told apart

Browsers state where a request comes from, in headers that pages cannot
change:

| Request | Outcome |
| ------- | ------- |
| GET, HEAD or OPTIONS | served |
| `Origin` among the trusted origins | served |
| `Sec-Fetch-Site: same-origin`, from a page of the app | served |
| `Sec-Fetch-Site: none`, a URL typed or a bookmark | served |
| `Sec-Fetch-Site: same-site` or `cross-site` | 403 |
| no `Sec-Fetch-Site` (browsers before 2023), `Origin` matching `Host` | served |
| no `Sec-Fetch-Site`, another `Origin` | 403 |
| neither header: curl, HTTP libraries, scripts | served |

Clients that are not browsers send neither header, and are served: they do
not carry the cookies of a user without being given them.

It works like [`http.CrossOriginProtection`](https://pkg.go.dev/net/http#CrossOriginProtection)
of Go 1.25.

## Single-page applications

A front end served by the app, or from the same origin through a reverse
proxy, needs nothing: its `fetch()` calls are `same-origin`. There is no
token to fetch, store or add to requests.

A front end served from another origin, allowed with [CORS](cors.md), must
be trusted too:

```java
String[] ui = { "https://ui.example.com" };
app.addBeforeAllHook(new Cors().allowOrigins(ui).allowCredentials(true));
app.addBeforeAllHook(new CrossOriginProtection().trustOrigins(ui));
```

## Behind a proxy

Browsers since 2023 send `Sec-Fetch-Site`, which needs no configuration.
Older ones are checked by comparing `Origin` with `Host`: a proxy that
rewrites the `Host` header makes their requests refused. Let it pass the
original one, or trust the public origin of the app with `trustOrigins()`.
