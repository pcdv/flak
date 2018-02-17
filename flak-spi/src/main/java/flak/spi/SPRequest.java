package flak.spi;

import flak.Request;

/**
 * @author pcdv
 */
public interface SPRequest extends Request {
  String[] getSplitUri();

  String getSplit(int tokenIndex);

  String getSplat(int tokenIndex);

}
