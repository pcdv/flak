package flak.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import flak.Request;

/**
 * Sends Server-Sent Events to every client connected to a route:
 * <pre>
 * SseBroadcaster prices = new SseBroadcaster();
 *
 * &#64;Route("/prices")
 * public void prices(Request req) {
 *   prices.serve(req);
 * }
 *
 * // from any thread
 * prices.publish(SseEvent.data(json).name("price"));
 * </pre>
 * Each client holds the thread that serves it for as long as it stays
 * connected, so the executor of the app must have enough threads.
 * <p>
 * Every client has a queue of the events that it has not received yet. A
 * client that falls more than {@link #maxQueuedEvents(int)} behind is
 * dropped, rather than letting its queue grow: no more events are queued for
 * it, and its stream ends once the write in progress completes. A browser
 * then reconnects, and can resume from {@link SseStream#getLastEventId()}.
 *
 * @since 3.5.0
 */
public class SseBroadcaster implements Closeable {

  /**
   * What a client does when it connects, before receiving the events
   * published from then on, e.g. sending those it missed.
   */
  public interface OpenListener {
    void opened(SseStream stream) throws IOException;
  }

  /**
   * Put in the queue of a client to end its stream.
   */
  private static final byte[] END = new byte[0];

  private final Set<Client> clients = ConcurrentHashMap.newKeySet();

  private volatile Duration keepAlive = Duration.ofSeconds(15);

  private volatile int maxQueuedEvents = 1000;

  private volatile boolean closed;

  /**
   * Sets how often a comment is sent to a client when no event was published,
   * 15 seconds by default. It keeps proxies from closing a quiet connection,
   * and it is how a client that went away is noticed, freeing its thread: a
   * write to a closed connection fails. Zero disables it.
   */
  public SseBroadcaster keepAlive(Duration keepAlive) {
    if (keepAlive.isNegative())
      throw new IllegalArgumentException("keepAlive must not be negative");
    this.keepAlive = keepAlive;
    return this;
  }

  /**
   * Sets how many events a client may have waiting before it is dropped,
   * 1000 by default.
   */
  public SseBroadcaster maxQueuedEvents(int maxQueuedEvents) {
    if (maxQueuedEvents < 1)
      throw new IllegalArgumentException("maxQueuedEvents must be positive");
    this.maxQueuedEvents = maxQueuedEvents;
    return this;
  }

  /**
   * Serves a client: opens its stream, and sends it the published events
   * until it goes away or {@link #close()} is called. Call this from a handler,
   * which it blocks until then.
   */
  public void serve(Request request) {
    serve(request, null);
  }

  /**
   * Same as {@link #serve(Request)}, calling a listener once the stream is
   * open. Events published while it runs are sent after it returns.
   *
   * @param onOpen called with the stream of the client, or null
   */
  public void serve(Request request, OpenListener onOpen) {
    Client client = new Client();
    // before opening the stream, so that no event published meanwhile is lost
    clients.add(client);
    try {
      if (closed) {
        // tells a browser not to reconnect
        request.getResponse().setStatus(HttpURLConnection.HTTP_NO_CONTENT);
        return;
      }

      SseStream stream = SseStream.open(request);
      if (onOpen != null)
        onOpen.opened(stream);

      while (true) {
        long timeout = keepAlive.toMillis();
        byte[] bytes = timeout == 0 ? client.queue.take()
                                    : client.queue.poll(timeout, TimeUnit.MILLISECONDS);
        if (bytes == END)
          return;
        if (bytes == null)
          stream.comment("");
        else
          stream.write(bytes);
      }
    }
    catch (IOException e) {
      // the client is gone
    }
    catch (InterruptedException e) {
      // the server is stopping
      Thread.currentThread().interrupt();
    }
    finally {
      clients.remove(client);
    }
  }

  /**
   * Sends an event to every connected client. Does not wait for it to be
   * sent: every client is served by its own thread.
   */
  public synchronized void publish(SseEvent event) {
    if (clients.isEmpty())
      return;
    byte[] bytes = event.encode();
    int max = maxQueuedEvents;
    for (Client c : clients) {
      if (c.queue.size() >= max)
        end(c);
      else
        c.queue.offer(bytes);
    }
  }

  /**
   * Returns the number of clients connected.
   */
  public int getClientCount() {
    return clients.size();
  }

  /**
   * Ends the streams of all clients, and answers 204 to the ones that connect
   * from then on, which tells browsers to stop reconnecting.
   */
  @Override
  public void close() {
    closed = true;
    for (Client c : clients)
      end(c);
  }

  private void end(Client c) {
    clients.remove(c);
    c.queue.clear();
    c.queue.offer(END);
  }

  private static class Client {
    final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
  }
}
