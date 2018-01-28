package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public class StringExtractor extends ArgExtractor {
  private int tokenIndex;

  public StringExtractor(int index, int tokenIndex) {
    super(index);
    this.tokenIndex = tokenIndex;
  }

  @Override
  public Object extract(JdkRequest request) {
    return request.getSplit(tokenIndex);
  }
}
