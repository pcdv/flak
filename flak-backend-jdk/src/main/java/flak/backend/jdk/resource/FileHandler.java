package flak.backend.jdk.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;

import flak.ContentTypeProvider;
import flak.backend.jdk.JdkApp;

public class FileHandler extends AbstractResourceHandler {

  private final Path localPath;

  public FileHandler(JdkApp app,
                     ContentTypeProvider mime,
                     String rootURI,
                     File localFile,
                     boolean requiresAuth) {
    super(app, mime, rootURI, requiresAuth);
    this.localPath = localFile.toPath();
  }

  @Override
  protected InputStream openPath(String p) throws FileNotFoundException {
    if (p.startsWith("/"))
      p = p.substring(1);
    return new FileInputStream(localPath.resolve(p).toFile());
  }
}
