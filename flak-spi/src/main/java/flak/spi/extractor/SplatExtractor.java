package flak.spi.extractor;

import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class SplatExtractor extends ArgExtractor<String> {
  private final int slashCount;

  public SplatExtractor(int index, String path) {
    super(index);
    this.slashCount = countSlashesBeforeSplat(path);
  }

  private int countSlashesBeforeSplat(String path) {
    int pos = path.lastIndexOf("/*");
    int count = 0;
    for (int i = pos - 1; i >= 0; i--) {
      if (path.charAt(i) == '/')
        count++;
    }
    return count;
  }

  @Override
  public String extract(SPRequest request) {
    return request.getSplat(slashCount);
  }
}
