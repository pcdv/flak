package flak.spi.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;

import flak.ContentTypeProvider;

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
  protected InputStream openPath(String p) throws FileNotFoundException {
    if (p.startsWith("/"))
      p = p.substring(1);
    return new FileInputStream(localPath.resolve(p).toFile());
  }
}
