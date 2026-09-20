package flask.test;

import flak.AppFactory;
import flak.WebServer;
import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;

import static java.net.InetAddress.getLoopbackAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BoundAddressTest {

  private WebServer ws;

  @After
  public void tearDown() {
    if (ws != null)
      ws.stop();
  }

  /**
   * Asserts the user can choose a random port
   * (address should default to 0.0.0.0)
   */
  @Test
  public void testRandomPortOnly() throws Exception {
    AppFactory fac = TestUtil.getFactory();
    fac.setPort(0);
    ws = fac.getServer();
    ws.start();

    assertTrue(ws.getLocalAddress().getAddress().isAnyLocalAddress());
    assertNotEquals(0, ws.getLocalAddress().getPort());
  }

  /**
   * Asserts the user can choose a specific port
   * (address should default to 0.0.0.0)
   */
  @Test
  public void testPortOnly() throws Exception {
    int port = TestUtil.findFreePort();

    AppFactory fac = TestUtil.getFactory();
    fac.setPort(port);
    ws = fac.getServer();
    ws.start();

    assertTrue(ws.getLocalAddress().getAddress().isAnyLocalAddress());
    assertEquals(port, ws.getLocalAddress().getPort());
  }

  /**
   * Asserts the user can choose both a random port and a specific IP address
   */
  @Test
  public void testRandomPortAndLocalIPAddress() throws Exception {
    AppFactory fac = TestUtil.getFactory();
    fac.setLocalAddress(new InetSocketAddress(getLoopbackAddress(), 0));
    ws = fac.getServer();
    ws.start();

    assertEquals(getLoopbackAddress(), ws.getLocalAddress().getAddress());
    assertNotEquals(0, ws.getLocalAddress().getPort());
  }

  /**
   * Asserts the user can choose both a specific port and a specific IP address
   */
  @Test
  public void testPortAndLocalIPAddress() throws Exception {
    int port = TestUtil.findFreePort();

    AppFactory fac = TestUtil.getFactory();
    fac.setLocalAddress(new InetSocketAddress(getLoopbackAddress(), port));
    ws = fac.getServer();
    ws.start();

    assertEquals(new InetSocketAddress(getLoopbackAddress(), port), ws.getLocalAddress());
  }
}
