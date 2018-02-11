package flak.backend.jdk.extractor;

import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class SplatExtractor extends ArgExtractor<String> {
  private int tokenIndex;

  public SplatExtractor(int index, int tokenIndex) {
    super(index);
    this.tokenIndex = tokenIndex;
  }

  @Override
  public String extract(SPRequest request) {
    return request.getSplat(tokenIndex);
  }
}
