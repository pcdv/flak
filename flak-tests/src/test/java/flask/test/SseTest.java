package flask.test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import flak.Request;
import flak.annotations.Compress;
import flak.annotations.Route;
import flak.util.SseBroadcaster;
import flak.util.SseEvent;
import flask.test.util.RawHttp;
import flask.test.util.SseClient;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SseTest extends AbstractAppTest {

  private final SseBroadcaster events = new SseBroadcaster();

  @Route("/events")
  public void events(Request req) {
    events.serve(req);
  }

  @Route("/resume")
  public void resume(Request req) {
    events.serve(req, s -> s.send(SseEvent.data("after " + s.getLastEventId())));
  }

  @Compress
  @Route("/compressed")
  public void compressed(Request req) {
    events.serve(req);
  }

  @After
  public void closeEvents() {
    events.close();
  }

  @Test
  public void testEncoding() {
    assertEquals("data: a\ndata: b\ndata: \ndata: c\n\n",
                 SseEvent.data("a\r\nb\r\rc").toString());
    assertEquals("event: price\nid: 42\nretry: 3000\ndata: {}\n\n",
                 SseEvent.data("{}").name("price").id("42")
                         .retry(Duration.ofSeconds(3)).toString());
    assertEquals("data: \n\n", SseEvent.data("").toString());
    assertThrows(IllegalArgumentException.class, () -> SseEvent.data("").name("a\nb"));
    assertThrows(IllegalArgumentException.class, () -> SseEvent.data("").id("1\r"));
  }

  @Test
  public void testPublishToAllClients() throws Exception {
    try (SseClient a = connect("/events");
         SseClient b = connect("/events")) {
      assertEquals(200, a.status());
      assertEquals("text/event-stream; charset=utf-8", a.header("Content-Type"));
      assertEquals("no-cache", a.header("Cache-Control"));
      assertEquals(2, events.getClientCount());

      events.publish(SseEvent.data("line 1\nline 2").name("msg").id("1"));
      List<String> expected = List.of("event: msg", "id: 1", "data: line 1", "data: line 2");
      assertEquals(expected, a.nextEvent());
      assertEquals(expected, b.nextEvent());

      events.publish(SseEvent.data("é"));
      assertEquals(List.of("data: é"), a.nextEvent());
    }
  }

  /**
   * A client that goes away is noticed by the next keepalive comment, which
   * frees its thread although nothing is published.
   */
  @Test
  public void testDisconnectedClientIsDropped() throws Exception {
    events.keepAlive(Duration.ofMillis(50));
    try (SseClient c = connect("/events")) {
      assertEquals(1, events.getClientCount());
    }
    waitFor(() -> events.getClientCount() == 0);
  }

  /**
   * A client that reads nothing must not make the server queue every event
   * published meanwhile.
   */
  @Test
  public void testSlowClientIsDisconnected() throws Exception {
    events.maxQueuedEvents(4);
    String big = "x".repeat(64 * 1024);
    try (SseClient c = connect("/events")) {
      int published = 0;
      // the socket buffers take a few MB before the server blocks on writing
      while (events.getClientCount() > 0 && published < 2000) {
        events.publish(SseEvent.data(big));
        published++;
      }
      assertEquals(0, events.getClientCount());

      // once read, the stream ends cleanly, short of what was published
      String body = c.readToEnd();
      int received = body.split("data: ", -1).length - 1;
      assertTrue(received + " of " + published, received < published);
    }
  }

  @Test
  public void testClose() throws Exception {
    try (SseClient c = connect("/events")) {
      events.close();
      // just the comment that opened the stream
      assertEquals(":\n\n", c.readToEnd());
    }
    waitFor(() -> events.getClientCount() == 0);
    assertEquals(204, RawHttp.send(app.getRootUrl(), "GET /events").status());
  }

  @Test
  public void testLastEventId() throws Exception {
    try (SseClient c = connect("/resume", "Last-Event-ID: 7")) {
      assertEquals(List.of("data: after 7"), c.nextEvent());
      events.publish(SseEvent.data("next"));
      assertEquals(List.of("data: next"), c.nextEvent());
    }
  }

  /**
   * What a handler writes into the output stream is never compressed.
   */
  @Test
  public void testCompressHasNoEffect() throws Exception {
    try (SseClient c = connect("/compressed", "Accept-Encoding: gzip")) {
      assertNull(c.header("Content-Encoding"));
      events.publish(SseEvent.data("plain"));
      assertEquals(List.of("data: plain"), c.nextEvent());
    }
  }

  private SseClient connect(String path, String... headers) throws IOException {
    // returns once the headers are in, and by then the client is registered
    return new SseClient(app.getRootUrl(), path, headers);
  }

  private static void waitFor(BooleanSupplier condition) throws InterruptedException {
    long end = System.currentTimeMillis() + 5000;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > end)
        throw new AssertionError("Timed out");
      Thread.sleep(10);
    }
  }
}
