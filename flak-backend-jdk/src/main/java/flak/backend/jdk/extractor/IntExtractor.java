package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;

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
  public Integer extract(JdkRequest request) {
    return Integer.valueOf(request.getSplit(tokenIndex));
  }
}
