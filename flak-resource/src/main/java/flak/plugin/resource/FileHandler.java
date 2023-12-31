package flak.plugin.resource;

import flak.annotations.Compress;
import flak.spi.SPResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHandler extends AbstractResourceHandler {

  private final Path localPath;

  public FileHandler(ContentTypeProvider mime,
                     String rootURI,
                     File localFile,
                     boolean requiresAuth) {
    super(mime, rootURI, requiresAuth);
    this.localPath = localFile.toPath();
  }

  @Override
  protected InputStream openPath(String p, SPResponse resp) throws IOException {
    if (p.startsWith("/"))
      p = p.substring(1);
    File file = localPath.resolve(p).toFile();
    if (file.length() > Compress.COMPRESS_THRESHOLD)
      resp.setCompressionAllowed(true);
    return Files.newInputStream(file.toPath());
  }
}
