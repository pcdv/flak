package flak.examples;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import flak.App;
import flak.Flak;
import flak.Request;
import flak.Response;
import flak.annotations.Route;
import flak.websocket.WebSocket;
import flak.websocket.WebSocketEndpoint;

/**
 * A chat room in a page, with flak-websocket: open it in several tabs.
 *
 * @author pcdv
 */
public class Chat {

  private static final String PAGE =
    "<!doctype html><title>Chat</title>" +
    "<pre id=log></pre><input id=msg autofocus placeholder='Say something'>" +
    "<script>" +
    "const ws = new WebSocket(location.href.replace(/^http/, 'ws') + 'ws');" +
    "ws.onmessage = e => log.textContent += e.data + '\\n';" +
    "ws.onclose = e => log.textContent += '[closed ' + e.code + ']\\n';" +
    "msg.onkeydown = e => { if (e.key === 'Enter') { ws.send(msg.value); msg.value = ''; } };" +
    "</script>";

  private final WebSocketEndpoint room = new WebSocketEndpoint() {
    @Override
    public void onOpen(WebSocket conn, Request handshake) {
      broadcast(conn.getAttachment() + " joined, " + getConnections().size() + " here");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      broadcast(conn.getAttachment() + ": " + message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      broadcast(conn.getAttachment() + " left");
    }
  };

  private int guests;

  @Route("/")
  public String page(Response response) {
    response.addHeader("Content-Type", "text/html");
    return PAGE;
  }

  @Route("/ws")
  public void ws(Request request) throws IOException {
    String name;
    synchronized (this) {
      name = "guest" + (++guests);
    }
    room.accept(request, name);
  }

  public static void main(String[] args) throws Exception {
    App app = Flak.createHttpApp(8080);
    app.scan(new Chat());
    app.start();
    Desktop.getDesktop().browse(new URI(app.getRootUrl() + "/"));
  }
}
