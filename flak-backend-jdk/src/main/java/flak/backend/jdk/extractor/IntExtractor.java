package flak.backend.jdk.extractor;

import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class IntExtractor extends ArgExtractor<Integer> {
  private int tokenIndex;

  public IntExtractor(int index, int tokenIndex) {
    super(index);
    this.tokenIndex = tokenIndex;
  }

  @Override
  public Integer extract(SPRequest request) {
    return Integer.valueOf(request.getSplit(tokenIndex));
  }
}
