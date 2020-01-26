package flask.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import flak.Response;
import flak.annotations.Route;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test shows that it is possible to stream data in chunked encoding
 * from the server to the client: the client reads data as soon as the
 * server writes it, without closing the connection.
 */
public class ChunkedEncodingTest extends AbstractAppTest {

  LinkedBlockingQueue<String> toSend = new LinkedBlockingQueue<>();

  @Route("/stream")
  public void stream(Response r) throws Exception {
    while (true) {
      String s = poll(toSend);
      if (s == null || "END".equals(s))
        break;
      r.getOutputStream().write(s.getBytes());
      r.getOutputStream().flush();
    }
  }

  private static String poll(LinkedBlockingQueue<String> queue) throws InterruptedException {
    return queue.poll(20, TimeUnit.SECONDS);
  }

  @Test
  public void testIt() throws Exception {
    LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    new Thread(() -> readInto(queue)).start();
    toSend.put("Hello\n");
    Assert.assertEquals("Hello", queue.poll(1, TimeUnit.SECONDS));
    toSend.put("world\n");
    Assert.assertEquals("world", queue.poll(1, TimeUnit.SECONDS));
    toSend.put("END");
  }

  private void readInto(LinkedBlockingQueue<String> queue) {
    try {
      InputStream in = client.doHTTP("/stream", null, "GET", null);
      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      for (String s = reader.readLine(); s != null; s = reader.readLine()) {
        queue.put(s);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
