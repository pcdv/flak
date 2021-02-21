package flak.spi.extractor;

import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class StringExtractor extends ArgExtractor<String> {
  private int tokenIndex;

  public StringExtractor(int index, int tokenIndex) {
    super(index);
    this.tokenIndex = tokenIndex;
  }

  @Override
  public String extract(SPRequest request) {
    return request.getSplit(tokenIndex);
  }
}
