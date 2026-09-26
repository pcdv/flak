package flak.plugin.resource;

import flak.spi.CompressionHelper;
import flak.spi.SPResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
  protected InputStream openPath(String p, SPResponse resp) throws IOException {
    Path file = resolve(p);
    if (file == null)
      throw new FileNotFoundException(p);
    try {
      if (Files.size(file) > CompressionHelper.COMPRESS_THRESHOLD)
        resp.setCompressionAllowed(true);
      return Files.newInputStream(file);
    }
    catch (NoSuchFileException e) {
      // unlike the File API, NIO does not throw FileNotFoundException, which
      // is what tells AbstractResourceHandler to answer 404 rather than 500
      throw new FileNotFoundException(p);
    }
  }
}
