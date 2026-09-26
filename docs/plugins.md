# Plugins

`flak-jackson` and `flak-login` are plugins. A plugin inspects the handlers
of an app as they are scanned, and can add hooks around them, argument
types, formatters or parsers.

## Installing plugins

By default, the plugins present on the classpath are found with
`ServiceLoader` and installed in every app the factory creates. Adding the
dependency is all it takes.

To choose them explicitly, list them on the factory:

```java
AppFactory factory = Flak.getFactory();
factory.setPlugins(JacksonPlugin.class, FlakLogin.class);
App app = factory.createApp();
```

Automatic discovery is then disabled: exactly those plugins are installed, in
that order. Naming a plugin whose module is missing from the classpath fails
immediately, rather than leaving the app quietly short of a feature.
`setPlugins()` with no argument installs no plugin at all.

`factory.setPluginValidator(cls -> ...)` is the older way to filter the
plugins that are discovered. It is ignored when `setPlugins()` is used.

A plugin can also be installed by hand. This is how an application's own
plugin is installed, since it has no loader to be discovered by:

```java
app.addPlugin(new MyPlugin(app));
```

`app.getPlugin(FlakLogin.class)` returns an installed plugin, to configure
it, and fails if it is not installed.

## Writing a plugin

Plugins use the service provider interface in `flak-spi`. It is less stable
than the public API: it can change between versions, which the
[migration notes](migration-3.0.md) then describe.

A plugin implements
[SPPlugin](../flak-spi/src/main/java/flak/spi/SPPlugin.java). `install()` is
called once, when it is added to an app. `preInit()` is called for each
handler of the app, before the handler is initialized: it is where the
plugin reads annotations and adds hooks. This example rejects requests to
handlers annotated with a custom `@InternalOnly` unless they come from the
local machine:

```java
public class InternalOnlyPlugin implements SPPlugin {
  @Override
  public void preInit(AbstractMethodHandler handler) {
    if (handler.getJavaMethod().isAnnotationPresent(InternalOnly.class)) {
      handler.addHook(req -> {
        if (!req.getRemoteAddress().getAddress().isLoopbackAddress())
          throw new HttpException(403, "Internal endpoint");
      });
    }
  }
}
```

A hook is a
[BeforeHook](../flak-api/src/main/java/flak/BeforeHook.java). It runs
before the arguments are extracted and the handler is called. It can reject
the request by throwing an `HttpException`. Alternatively, it can write the
response itself and throw `BeforeHook.STOP`, as `flak-login` does when it
redirects to the login page.

Besides hooks, `preInit()` can call `setOutputFormatter()` or
`setInputParser()` on the handler, as `flak-jackson` does for `@JSON`. By
then, `getParameters()` tells what each parameter is bound to, e.g. which one
is the body and of which type. A plugin that brings argument types registers
them from `install()`, with `app.addCustomExtractor()`.

For the plugin to be discovered, give it a
[FlakPluginLoader](../flak-spi/src/main/java/flak/spi/FlakPluginLoader.java):

```java
public class InternalOnlyPluginLoader implements FlakPluginLoader {
  @Override
  public void installPlugin(App app) {
    app.addPlugin(new InternalOnlyPlugin());
  }

  @Override
  public Class<? extends FlakPlugin> getPluginClass() {
    return InternalOnlyPlugin.class;
  }
}
```

Then list the loader in `META-INF/services/flak.spi.FlakPluginLoader`.
