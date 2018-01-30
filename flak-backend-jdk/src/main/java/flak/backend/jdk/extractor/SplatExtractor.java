package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;

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
  public String extract(JdkRequest request) {
    return request.getSplat(tokenIndex);
  }
}
