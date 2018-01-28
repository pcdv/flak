package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;
import flak.backend.jdk.extractor.ArgExtractor;

/**
 * @author pcdv
 */
public class SplatExtractor extends ArgExtractor {
  private int tokenIndex;

  public SplatExtractor(int index, int tokenIndex) {
    super(index);
    this.tokenIndex = tokenIndex;
  }

  @Override
  public Object extract(JdkRequest request) {
    return request.getSplat(tokenIndex);
  }
}
