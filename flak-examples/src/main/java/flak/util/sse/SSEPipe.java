package flak.util.sse;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Queue that allows to pop messages to transmit to a HTTP client.
 */
public class SSEPipe {
  private final LinkedBlockingQueue<byte[]> queue;

  public SSEPipe() {
    this.queue = new LinkedBlockingQueue<>();
  }

  public byte[] pop() throws InterruptedException {
    return queue.take();
  }

  public boolean offer(byte[] data) {
    return queue.offer(data);
  }
}
