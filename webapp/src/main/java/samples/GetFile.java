package samples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import flak.annotations.Route;

/**
 * This sample shows how files can be served from the file system.
 */
public class GetFile {

  private final Path root;

  /**
   * @param root only files under this path will be served
   */
  public GetFile(Path root) {
    this.root = root;
  }

  @Route("/file/*path")
  public byte[] getFile(String path) throws IOException {
    if (path.contains(".."))
      throw new IllegalArgumentException("Invalid path");
    return Files.readAllBytes(root.resolve(path));
  }
}
