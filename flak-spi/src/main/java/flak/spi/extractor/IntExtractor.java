package flak.spi.extractor;

import flak.HttpException;
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

  /**
   * A path that is not a number designates no resource: 404, as JAX-RS
   * answers a path parameter it cannot convert, rather than a server error.
   */
  @Override
  public Integer extract(SPRequest request) {
    String s = request.getSplit(tokenIndex);
    try {
      return Integer.valueOf(s);
    }
    catch (NumberFormatException e) {
      throw new HttpException(404, "Not found");
    }
  }
}
