package flask.test.util;

import flak.spi.util.IO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class DebugProxy {

  private final ServerSocket srv;
  private final String host;
  private final int port;
  private final Thread listen;

  public DebugProxy(int listenPort, String host, int port) throws IOException {
    this.srv = new ServerSocket(listenPort);
    this.host = host;
    this.port = port;
    this.listen = new Thread(this::listen);
    this.listen.start();
  }

  public void close() throws IOException {
    srv.close();
  }

  static class PipeThread extends Thread {
    private final InputStream in;
    private final OutputStream out;

    public PipeThread(InputStream in, OutputStream out) {
      this.in = in;
      this.out = out;
    }

    public void run() {
      try {
        IO.pipe(in, out, true);
      }
      catch (IOException e) {
      }
    }
  }

  private void listen() {
    try {
      while (true) {
        Socket s = srv.accept();
        System.err.println("Proxy: Received connection");
        Socket o = new Socket(host, port);
        new PipeThread(s.getInputStream(), o.getOutputStream()).start();
        new PipeThread(o.getInputStream(), new TeeOutputStream(s.getOutputStream(), System.out)).start();
      }
    }
    catch (IOException e) {
    }
  }
}
