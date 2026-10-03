# CORS

*Since Flak 3.2.0, in flak-util.*

A browser lets a page call another origin, i.e. another scheme, host or
port, but hides the response from it unless the server allows that origin
with the headers of CORS (Cross-Origin Resource Sharing). This matters when
the front end is served from elsewhere than the API, e.g. from
`https://ui.example.com` calling `https://api.example.com`. A front end
served by the app itself, or from the same origin through a reverse proxy,
needs none of this.

```groovy
implementation "com.github.pcdv.flak:flak-util:3.2.0"
```

`Cors` is a [hook that runs before every request](errors-and-hooks.md#hooks-before-every-request):

```java
app.addBeforeAllHook(new Cors().allowOrigins("https://ui.example.com"));
```

It adds `Access-Control-Allow-Origin` to the responses to requests from an
allowed origin, error responses included, so that the page can read why its
request failed.

## Preflight requests

Before most requests, e.g. a PUT, a DELETE, a JSON body or an
`Authorization` header, a browser asks for permission with an `OPTIONS`
request. `Cors` answers it with 204 and the methods and headers allowed, or
with 403 for an origin or method that is not. Either way, the request goes
no further:

- no route needs to accept `OPTIONS`, and an `@Options` handler never sees a
  preflight request
- flak-login does not reject it, although browsers send it without cookies,
  and before routes restricted with `@LoginRequired`

## Settings

```java
new Cors().allowOrigins("https://ui.example.com", "http://localhost:3000")
          .allowMethods("GET", "POST")
          .allowHeaders("Content-Type", "Authorization")
          .exposeHeaders("X-Total-Count")
          .allowCredentials(true)
          .maxAge(Duration.ofHours(1));
```

| Method | Default |
| ------ | ------- |
| `allowOrigins(...)` | none. An origin is written as in the Origin header: `https://host`, with `:port` unless it is the default one |
| `allowAnyOrigin()` | off. Rather list the origins when the routes rely on cookies |
| `allowMethods(...)` | GET, HEAD, POST, PUT, PATCH, DELETE |
| `allowHeaders(...)` | whatever headers the browser asks for |
| `exposeHeaders(...)` | none. The response headers a page may read, besides basic ones such as Content-Type |
| `allowCredentials(true)` | off. Lets pages send cookies, with `fetch(url, {credentials: "include"})` |
| `maxAge(...)` | 10 minutes. How long browsers may keep the answer to a preflight request |

## Cookies

With `allowCredentials(true)`, the session cookie of [flak-login](login.md)
goes with the requests of the page:

- to another host of the same domain, e.g. from `ui.example.com` to
  `api.example.com`, as it is: these are a same *site*, which is what the
  SameSite attribute of the cookie is about.
- to another domain, only if the cookie is `SameSite=None; Secure`, see
  `setSameSite()` in [Authentication](login.md). Even then, Safari and
  Firefox block such cookies by default. Prefer a token in an
  `Authorization` header there.

CORS restricts what pages can read, not who calls the app: a request from an
origin that is not allowed is still served, merely without the headers.
