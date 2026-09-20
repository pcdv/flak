package flask.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.AppFactory;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.jackson.JSON;
import flak.jackson.JacksonPlugin;
import flak.login.FlakLogin;
import flak.login.FlakUser;
import flask.test.util.SimpleClient;
import flask.test.util.ThreadState;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks that an app can be given the exact list of plugins it needs, instead
 * of getting whatever happens to be in the classpath.
 */
public class PluginSelectionTest {

  @Rule
  public ThreadState.ThreadStateRule noZombies =
      new ThreadState.ThreadStateRule("globalEventExecutor-.*");

  private App app;

  @After
  public void tearDown() {
    if (app != null)
      app.stop();
  }

  private AppFactory newFactory() throws Exception {
    AppFactory fac = TestUtil.getFactory();
    fac.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    return fac;
  }

  /**
   * Without setPlugins(), everything in the classpath is installed, as before.
   */
  @Test
  public void discoveryIsStillTheDefault() throws Exception {
    app = newFactory().createApp();

    assertNotNull(app.getPlugin(FlakLogin.class));
    assertNotNull(app.getPlugin(JacksonPlugin.class));
  }

  @Test
  public void noArgumentInstallsNoPlugin() throws Exception {
    AppFactory fac = newFactory();
    fac.setPlugins();
    app = fac.createApp();

    assertNotInstalled(FlakLogin.class);
    assertNotInstalled(JacksonPlugin.class);
  }

  @Test
  public void onlyTheRequestedPluginIsInstalled() throws Exception {
    AppFactory fac = newFactory();
    fac.setPlugins(FlakLogin.class);
    app = fac.createApp();

    assertNotNull(app.getPlugin(FlakLogin.class));
    assertNotInstalled(JacksonPlugin.class);
  }

  /**
   * The plugins must be fully functional, not merely present: FlakLogin
   * registers argument extractors and Jackson an output formatter.
   */
  @Test
  public void requestedPluginsAreFullyInstalled() throws Exception {
    AppFactory fac = newFactory();
    fac.setPlugins(JacksonPlugin.class, FlakLogin.class);
    app = fac.createApp();

    app.scan(new Object() {
      @Route("/api/echo")
      @Post
      @JSON
      public Map<String, Object> echo(FlakUser user, Map<String, Object> body) {
        // the FlakUser argument comes from FlakLogin, the JSON conversion of
        // the body and of the returned map from JacksonPlugin
        body.put("user", String.valueOf(user));
        return body;
      }
    });
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    String reply = client.post("/api/echo", "{\"hello\":\"world\"}");

    Map<?, ?> r = new ObjectMapper().readValue(reply, Map.class);
    assertEquals("world", r.get("hello"));
    assertEquals("null", r.get("user"));
  }

  /**
   * A plugin whose module is absent from the classpath, or a class that is not
   * a plugin at all, must be reported immediately: silently running without it
   * is how a login check ends up not protecting anything.
   */
  @Test
  public void missingPluginIsReported() throws Exception {
    AppFactory fac = newFactory();
    fac.setPlugins(FlakLogin.class, String.class);

    try {
      app = fac.createApp();
      fail("Expected an error about java.lang.String");
    }
    catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(),
                 e.getMessage().startsWith("No plugin found for java.lang.String"));
      // the message must help: say what IS available
      assertTrue(e.getMessage(), e.getMessage().contains(FlakLogin.class.getName()));
    }
  }

  /**
   * Nothing is installed when one plugin of the list is unknown, so that a typo
   * cannot leave a half configured app behind.
   */
  @Test
  public void nothingIsInstalledWhenOnePluginIsMissing() throws Exception {
    AppFactory fac = newFactory();
    fac.setPlugins(FlakLogin.class, String.class);

    try {
      fac.createApp();
      fail("Expected an error about java.lang.String");
    }
    catch (IllegalArgumentException expected) {
    }

    // the failed app is unusable, but the factory is still fine once fixed
    fac.setPlugins(FlakLogin.class);
    app = fac.createApp();
    assertNotNull(app.getPlugin(FlakLogin.class));
  }

  /**
   * Adding a plugin by hand must install it completely. It used to add it to
   * the list without calling install(), so FlakLogin's argument extractors
   * were silently lost and a handler taking a FlakUser failed with a message
   * about @JSON.
   */
  @Test
  public void handAddedPluginIsFullyInstalled() throws Exception {
    AppFactory fac = newFactory();
    fac.setPlugins();
    app = fac.createApp();

    app.addPlugin(new FlakLogin(app));

    app.scan(new Object() {
      @Route("/who")
      public String who(FlakUser user) {
        return String.valueOf(user);
      }
    });
    app.start();

    SimpleClient client = new SimpleClient(app.getRootUrl());
    assertEquals("null", client.get("/who"));
  }

  private void assertNotInstalled(Class<?> plugin) {
    try {
      app.getPlugin(plugin.asSubclass(flak.FlakPlugin.class));
      fail(plugin.getName() + " should not be installed");
    }
    catch (NoSuchElementException e) {
      assertTrue(e.getMessage(), e.getMessage().contains(plugin.getName()));
    }
  }
}
