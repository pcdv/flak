package flask.test;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * A file that changes while it is served must not reach the client as a short
 * response that looks complete: a browser would cache it, for a year with an
 * immutable Cache-Control. A file that gets shorter breaks the response, and
 * one that grows is sent with the size it had when opened.
 * <p>
 * The file is much bigger than what the socket buffers hold, so the server is
 * still reading it when the test changes it, after the client read its start.
 */
public class ResourceChangedWhileServedTest extends AbstractAppTest {

  private static final int SIZE = 32 << 20;

  private static final int READ_FIRST = 4 << 20;

  private Path dir;

  private Path file;

  private InputStream startDownload() throws IOException {
    dir = Files.createTempDirectory("flak");
    file = dir.resolve("big.bin");
    Files.write(file, new byte[SIZE]);
    app.serveDir("/files", dir.toFile());

    HttpURLConnection con =
      (HttpURLConnection) new URL(app.getRootUrl() + "/files/big.bin").openConnection();
    assertEquals(200, con.getResponseCode());
    InputStream in = con.getInputStream();
    assertEquals(READ_FIRST, in.readNBytes(READ_FIRST).length);
    return in;
  }

  @After
  public void deleteFile() throws IOException {
    if (file != null)
      Files.deleteIfExists(file);
    if (dir != null)
      Files.deleteIfExists(dir);
  }

  @Test
  public void fileGetsShorter() throws Exception {
    InputStream in = startDownload();

    // rewritten in place, below what the server has already read
    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
      ch.truncate(1 << 20);
    }

    try {
      int rest = in.readAllBytes().length;
      fail("A response cut short must fail, got " + (READ_FIRST + rest) + " bytes");
    }
    catch (IOException expected) {
      // e.g. "Premature EOF": the response has no last chunk
    }
    in.close();

    // the server remains usable
    assertEquals(1 << 20, client.get("/files/big.bin").length());
  }

  @Test
  public void fileGrows() throws Exception {
    InputStream in = startDownload();

    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.APPEND)) {
      ch.write(ByteBuffer.wrap(new byte[1 << 20]));
    }

    assertEquals(SIZE - READ_FIRST, in.readAllBytes().length);
    in.close();
  }
}
