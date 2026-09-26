# Authentication

`flak-login` restricts routes to logged-in users, and to users with given
permissions. The design is close to
[Flask-Login](https://flask-login.readthedocs.io/): Flak manages sessions and
checks them, while the application decides who its users are and how they
log in. It is a [plugin](plugins.md): adding the dependency installs it.

```groovy
implementation "com.github.pcdv.flak:flak-login:3.0"
```

## Overview

- A **user** is a [FlakUser](../flak-login/src/main/java/flak/login/FlakUser.java):
  an id and a set of permissions. `DefaultUser` is a ready-made one.
- A **session** is opened when a user logs in. It is identified by a random
  token, which the browser keeps in a cookie and sends back with each request.
- The **session manager** opens, finds and closes sessions.
  `DefaultSessionManager` keeps them in memory.
- **Annotations** say which routes need a logged-in user or a permission.

## A login flow

```java
public class Auth {
  private final App app;
  private final DefaultSessionManager sessions = new DefaultSessionManager();

  public Auth(App app) {
    this.app = app;
    app.getPlugin(FlakLogin.class).setSessionManager(sessions);
  }

  @LoginPage
  @Route("/login")
  public InputStream loginPage() {
    return getClass().getResourceAsStream("/login.html");
  }

  @LoginNotRequired
  @Route("/login")
  @Post
  public void login(Form form, Response r) {
    DefaultUser user = findUser(form.get("user"), form.get("password"));
    if (user == null)
      throw new HttpException(401, "Invalid login");

    sessions.openSession(app, new DefaultFlakSession(user, sessions.generateSessionToken()), r);
    r.redirect(safeTarget(form.get("url")));
  }

  @Route("/logout")
  public void logout(Request req, Response r) {
    sessions.closeCurrentSession(req);
    r.redirect("/login");
  }

  @LoginRequired
  @Route("/account")
  public String account(FlakUser user) {
    return "Logged in as " + user.getId();
  }
}
```

- `@LoginPage` marks the handler of the login page. A user who is not logged
  in and asks for a restricted route is redirected there. The page itself,
  and anything posted to its path, is always accessible.
- The login handler checks the credentials and opens a session. The session
  manager then sets the cookie in the response.
- `@LoginRequired` restricts a route to logged-in users.
- A `FlakUser` parameter gives the logged-in user, or `null` if there is
  none. A `SessionManager` parameter gives the session manager.

`findUser()` stands for the application's own user store: flak-login does not
store users or passwords.

### Back to the requested page

When a user is redirected to the login page, the URL they asked for is passed
in a `url` parameter, query string included and url-encoded, e.g.
`/login?url=%2Faccount%3Ftab%3D2`. For the user to return there once logged
in, the login page must pass it on with the form, typically in a hidden field.
The login handler then redirects to it.

Check the value before redirecting to it, or the login page becomes an open
redirect to any site:

```java
private static String safeTarget(String url) {
  // a path of this app only: not "https://…", nor "//evil.example"
  return url != null && url.startsWith("/") && !url.startsWith("//") ? url : "/";
}
```

## Restricting routes

| Annotation | Effect |
| ---------- | ------ |
| `@LoginRequired` | the route needs a logged-in user |
| `@LoginNotRequired` | the route is public, even when login is required by default |
| `@LoginPage` | the route is the login page, and is public |
| `@WithPermission("admin")` | the route needs a user with that permission |
| `@WithAnyPermission({"read", "admin"})` | the route needs a user with at least one of them |

To make every route private unless it says otherwise:

```java
sessions.setRequireLoggedInByDefault(true);
```

A user who is not logged in and asks for a restricted route is:

- redirected to the login page, if there is one (`@LoginPage`, or
  `sessions.setLoginPage("/login")`)
- answered with **401** otherwise, which suits APIs

### Permissions

```java
DefaultUser alice = new DefaultUser("alice");
alice.addPermission("admin");
```

A permission implies a logged-in user: a user who is not logged in is
treated as for `@LoginRequired`. A logged-in user who lacks the permission
gets **403** with "No permission".

`@WithPermission` and `@WithAnyPermission` can also be put on a class. They
then apply to all its routes, and to those of its subclasses, except the
routes that carry one of them on the method: the annotation of the method
replaces that of its class.

```java
@WithPermission("admin")
public class AdminRoutes {
  @Route("/admin/users")
  public String users() { ... }            // requires "admin"

  @WithPermission("support")
  @Route("/admin/status")
  public String status() { ... }           // requires "support", not "admin"
}
```

## Sessions

`DefaultFlakSession(user, token)` never expires. To make a session expire,
pass the time it expires at, in milliseconds since the epoch:

```java
long expiry = System.currentTimeMillis() + Duration.ofHours(8).toMillis();
sessions.openSession(app, new DefaultFlakSession(user, sessions.generateSessionToken(), expiry), r);
```

The cookie then expires at the same time. A request presenting an expired
session is treated as not logged in, and the session is discarded. Expired
sessions that are never presented again are closed when another session
opens, at most once a minute, so they do not pile up in memory.

Other useful methods of the session manager:

| Method | Does |
| ------ | ---- |
| `getCurrentSession(request)` | the session of the request, or `null` |
| `isLoggedIn(request)` | whether the request belongs to a valid session |
| `closeCurrentSession(request)` | logs the current user out |
| `closeSession(session)` | ends any session, e.g. of a user being deleted |
| `getSessionForToken(token)` | looks a session up by its token |

A session opened by a request is not visible in that same request, since
the request does not carry the cookie yet. It is visible from the next one.

`DefaultSessionManager` keeps sessions in memory, so they are lost on
restart. To persist them, extend it: override `addSession()` and
`closeSession()` to save the changes (calling `super`), and load the saved
sessions into its protected `sessions` map, keyed by token, when the
application starts.

### The cookie

The token travels in a cookie:

- named `sessionToken`, which `sessions.setAuthTokenCookieName("myapp")`
  changes. Give each app its own name when several apps share a host name on
  different ports.
- with the path of the app, so that two apps on one server have separate
  sessions
- `HttpOnly` and `SameSite=Strict` by default. Change them per session with
  `setHttpOnly()` and `setSameSite()` on `DefaultFlakSession`.
- with `Expires` when the session expires
- `Secure` when the app is served over HTTPS, so that browsers never send
  the token in clear

Behind a proxy that terminates TLS, Flak serves plain HTTP and cannot tell.
To mark the cookie `Secure` anyway, override `generateSetCookieHeader()`:

```java
DefaultSessionManager sessions = new DefaultSessionManager() {
  @Override
  protected String generateSetCookieHeader(String path, FlakSession session) {
    return super.generateSetCookieHeader(path, session) + "; Secure";
  }
};
```

## Static resources

Resources served by [flak-resource](static-resources.md) can be restricted to
logged-in users, and the login page can itself be a static file:

```java
FlakResourceImpl resources = new FlakResourceImpl(app);
resources.servePath("/app", "/webapp", null, true);   // restricted
resources.servePath("/public", "/public");            // login.html is in there
sessions.setLoginPage("/public/login.html");
```

## Custom authentication

Sessions are only one way to authenticate. To accept other credentials, such
as an API token or HTTP Basic authentication, extend the session manager.
It must recognize them in `isLoggedIn()` and return a session for them from
`getCurrentSession()`:

```java
public class TokenSessionManager extends DefaultSessionManager {
  private final Map<String, FlakUser> apiTokens;

  public TokenSessionManager(Map<String, FlakUser> apiTokens) {
    this.apiTokens = apiTokens;
  }

  @Override
  public boolean isLoggedIn(Request r) {
    return super.isLoggedIn(r) || userForToken(r) != null;
  }

  @Override
  public FlakSession getCurrentSession(Request r) {
    FlakSession session = super.getCurrentSession(r);
    if (session == null) {
      FlakUser user = userForToken(r);
      if (user != null)
        return new DefaultFlakSession(user, "api-token");
    }
    return session;
  }

  private FlakUser userForToken(Request r) {
    String header = r.getHeader("Authorization");
    return header != null && header.startsWith("Bearer ")
      ? apiTokens.get(header.substring(7))
      : null;
  }
}
```

Install it with `app.getPlugin(FlakLogin.class).setSessionManager(...)`.
