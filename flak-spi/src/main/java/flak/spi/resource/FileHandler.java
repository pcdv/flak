package flak.spi.resource;

import flak.ContentTypeProvider;

import flak.spi.CompressionHelper;
import flak.spi.SPResponse;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class FileHandler extends AbstractResourceHandler {

  private final Path localPath;

  public FileHandler(ContentTypeProvider mime,
                     String rootURI,
                     File localFile,
                     boolean requiresAuth) {
    super(mime, rootURI, requiresAuth);
    this.localPath = localFile.toPath().toAbsolutePath().normalize();
  }

  /**
   * Resolves a path relative to the served directory, or returns null if it
   * is invalid or lies outside of it.
   */
  private Path resolve(String p) {
    if (p.startsWith("/"))
      p = p.substring(1);
    Path file;
    try {
      file = localPath.resolve(p).normalize();
    }
    catch (InvalidPathException e) {
      return null;
    }
    // besides "..", which is rejected upstream, an absolute path (e.g.
    // "/etc/passwd" or "C:/...") would make resolve() ignore the root
    return file.startsWith(localPath) ? file : null;
  }

  @Override
  protected boolean isDirectory(String p) {
    Path file = resolve(p);
    return file != null && Files.isDirectory(file);
  }

  @Override
  protected long lastModified(String p) {
    Path file = resolve(p);
    if (file == null)
      return 0;
    try {
      return Files.getLastModifiedTime(file).toMillis();
    }
    catch (IOException e) {
      return 0;
    }
  }

  @Override
  protected InputStream openPath(String p, SPResponse resp) throws IOException {
    Path file = resolve(p);
    if (file == null)
      throw new FileNotFoundException(p);
    try {
      // the size is the one of the file opened, not of the file at that path
      // a moment before or after, which may have been replaced in between
      FileChannel channel = FileChannel.open(file);
      long size;
      try {
        size = channel.size();
      }
      catch (IOException e) {
        channel.close();
        throw e;
      }
      if (size > CompressionHelper.COMPRESS_THRESHOLD)
        resp.setCompressionAllowed(true);
      return new SizedInputStream(Channels.newInputStream(channel), size, p);
    }
    catch (NoSuchFileException e) {
      // unlike the File API, NIO does not throw FileNotFoundException, which
      // is what tells AbstractResourceHandler to answer 404 rather than 500
      throw new FileNotFoundException(p);
    }
  }

  /**
   * Reads a file up to the size it had when opened. A file that grows in the
   * meantime is cut at that size, as a snapshot of when it was opened. A file
   * that gets shorter, e.g. rewritten in place, fails with an EOFException:
   * the response must then be broken, not short.
   */
  static class SizedInputStream extends InputStream {

    private final InputStream in;

    private final String path;

    private long left;

    SizedInputStream(InputStream in, long size, String path) {
      this.in = in;
      this.left = size;
      this.path = path;
    }

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      return read(b, 0, 1) < 0 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (len == 0)
        return 0;
      if (left == 0)
        return -1;
      int n = in.read(b, off, (int) Math.min(len, left));
      if (n < 0)
        throw new EOFException(path + " got shorter while being read: "
                               + left + " bytes missing");
      left -= n;
      return n;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }
  }
}
