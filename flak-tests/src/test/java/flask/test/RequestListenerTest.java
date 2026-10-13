package flask.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import flak.HttpException;
import flak.Request;
import flak.RequestListener;
import flak.RequestSummary;
import flak.annotations.Compress;
import flak.annotations.Post;
import flak.annotations.Route;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.Assert;
import org.junit.Test;

public class RequestListenerTest extends AbstractAppTest {

  private static final String BIG = "a".repeat(100_000);

  private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();

  {
    // the first recording starts threads that the JVM keeps
    noZombies.ignore("JFR .*");
  }

  @Route("/hello/:name")
  public String hello(String name) {
    events.offer("handler " + Thread.currentThread().getName());
    return "Hello " + name;
  }

  @Route("/barf")
  public String barf() {
    throw new RuntimeException("barf");
  }

  @Route("/echo")
  @Post
  public byte[] echo(Request req) throws IOException {
    return req.getInputStream().readAllBytes();
  }

  @Route("/big")
  public String big() {
    return BIG;
  }

  @Route("/big/compressed")
  @Compress
  public String bigCompressed() {
    return BIG;
  }

  @Override
  protected void preStart() {
    app.addRequestListener(new RequestListener() {
      @Override
      public void onBegin(Request r) {
        events.offer("begin " + r.getRoute() + " " + Thread.currentThread().getName());
      }

      @Override
      public void onEnd(Request r, RequestSummary s) {
        events.offer("end " + s.getRoute() + " " + s.getStatus() +
                     " in=" + s.getBytesIn() + " out=" + s.getBytesOut() +
                     " " + Thread.currentThread().getName());
      }
    });
  }

  private String next() throws InterruptedException {
    // the end comes after the response, which the client may read first
    String e = events.poll(5, TimeUnit.SECONDS);
    Assert.assertNotNull("no event", e);
    return e;
  }

  @Test
  public void testBeginThenEndOnTheServingThread() throws Exception {
    Assert.assertEquals("Hello world", client.get("/hello/world"));

    String begin = next();
    String handler = next();
    String end = next();
    String thread = handler.substring("handler ".length());

    Assert.assertEquals("begin /hello/:name " + thread, begin);
    Assert.assertEquals("end /hello/:name 200 in=0 out=11 " + thread, end);
  }

  @Test
  public void testNoRouteEndsWithoutBeginning() throws Exception {
    TestUtil.assertFails(() -> client.get("/unknown"), "404");
    String end = next();
    Assert.assertTrue(end, end.startsWith("end null 404 "));
  }

  @Test
  public void testFailure() throws Exception {
    TestUtil.assertFails(() -> client.get("/barf"), "500");
    Assert.assertTrue(next().startsWith("begin /barf "));
    Assert.assertTrue(next().startsWith("end /barf 500 "));
  }

  @Test
  public void testBytesIn() throws Exception {
    Assert.assertEquals("12345", client.post("/echo", "12345"));
    next();
    String end = next();
    Assert.assertTrue(end, end.startsWith("end /echo 200 in=5 out=5 "));
  }

  @Test
  public void testBigResponse() throws Exception {
    Assert.assertEquals(BIG, client.get("/big"));
    next();
    String end = next();
    Assert.assertTrue(end, end.startsWith("end /big 200 in=0 out=100000 "));
  }

  @Test
  public void testCompressedResponseCountsWhatIsSent() throws Exception {
    Assert.assertEquals(BIG, client.get("/big/compressed"));
    next();
    String end = next();
    long out = Long.parseLong(end.replaceAll(".* out=(\\d+) .*", "$1"));
    Assert.assertTrue(end, out > 0 && out < 1000);
  }

  @Test
  public void testFailingListenerDoesNotBreakRequest() throws Exception {
    app.addRequestListener(new RequestListener() {
      @Override
      public void onBegin(Request request) {
        throw new IllegalStateException("begin");
      }

      @Override
      public void onEnd(Request request, RequestSummary summary) {
        throw new IllegalStateException("end");
      }
    });
    Assert.assertEquals("Hello you", client.get("/hello/you"));
  }

  @Test
  public void testRemoveListener() throws Exception {
    RequestListener l = new RequestListener() {
      @Override
      public void onEnd(Request request, RequestSummary summary) {
        events.offer("removed listener called");
      }
    };
    app.addRequestListener(l);
    app.removeRequestListener(l);
    client.get("/hello/x");
    for (int i = 0; i < 3; i++)
      Assert.assertNotEquals("removed listener called", next());
  }

  @Test
  public void testJfrEvent() throws Exception {
    Path file = Files.createTempFile("flak", ".jfr");
    try {
      try (Recording rec = new Recording()) {
        rec.enable("flak.Request");
        rec.start();
        client.get("/hello/jfr");
        TestUtil.assertFails(() -> client.get("/unknown"), "404");
        // the event is committed when the response is finished, which the
        // client may see first
        next();
        next();
        next();
        next();
        rec.stop();
        rec.dump(file);
      }

      List<RecordedEvent> events = RecordingFile.readAllEvents(file)
                                                .stream()
                                                .filter(e -> e.getEventType().getName().equals("flak.Request"))
                                                .collect(Collectors.toList());
      Assert.assertEquals(2, events.size());

      // events are not in chronological order: each thread has its own buffer
      RecordedEvent hello = eventWithPath(events, "/hello/jfr");
      Assert.assertEquals(app.getPath(), hello.getString("app"));
      Assert.assertEquals("GET", hello.getString("method"));
      Assert.assertEquals("/hello/:name", hello.getString("route"));
      Assert.assertEquals("/hello/jfr", hello.getString("path"));
      Assert.assertEquals(200, hello.getInt("status"));
      Assert.assertEquals(0, hello.getLong("bytesIn"));
      Assert.assertEquals(9, hello.getLong("bytesOut"));
      Assert.assertTrue(hello.getDuration().toNanos() > 0);
      Assert.assertNotNull(hello.getThread());

      RecordedEvent unknown = eventWithPath(events, "/unknown");
      Assert.assertNull(unknown.getString("route"));
      Assert.assertEquals(404, unknown.getInt("status"));
    }
    finally {
      Files.deleteIfExists(file);
    }
  }

  private static RecordedEvent eventWithPath(List<RecordedEvent> events, String path) {
    return events.stream()
                 .filter(e -> path.equals(e.getString("path")))
                 .findFirst()
                 .orElseThrow(() -> new AssertionError("No event for " + path));
  }

  /**
   * What a profiler does to record the very request that made it start: the
   * event of that request was begun before the recording existed.
   */
  @Test
  public void testJfrEventOfRequestThatStartsTheRecording() throws Exception {
    Path file = Files.createTempFile("flak", ".jfr");
    try (Recording rec = new Recording()) {
      rec.enable("flak.Request");
      app.addRequestListener(new RequestListener() {
        @Override
        public void onBegin(Request request) {
          rec.start();
        }
      });

      java.time.Instant before = java.time.Instant.now();
      client.get("/hello/start");
      next();
      next();
      next();
      rec.stop();
      rec.dump(file);

      List<RecordedEvent> events = RecordingFile.readAllEvents(file)
                                                .stream()
                                                .filter(e -> e.getEventType().getName().equals("flak.Request"))
                                                .collect(Collectors.toList());
      Assert.assertEquals(1, events.size());
      RecordedEvent e = events.get(0);
      Assert.assertEquals("/hello/:name", e.getString("route"));
      // the request is bounded by when it really ran
      Assert.assertFalse(e.getStartTime().toString(), e.getStartTime().isBefore(before));
      Assert.assertTrue(e.getDuration().toString(), e.getDuration().toMillis() < 5000);
    }
    finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void testJfrEventDisabledByDefault() throws Exception {
    Path file = Files.createTempFile("flak", ".jfr");
    try {
      try (Recording rec = new Recording(jdk.jfr.Configuration.getConfiguration("default"))) {
        rec.start();
        client.get("/hello/jfr");
        next();
        next();
        next();
        rec.stop();
        rec.dump(file);
      }
      Assert.assertTrue(RecordingFile.readAllEvents(file)
                                     .stream()
                                     .noneMatch(e -> e.getEventType().getName().equals("flak.Request")));
    }
    finally {
      Files.deleteIfExists(file);
    }
  }
}
