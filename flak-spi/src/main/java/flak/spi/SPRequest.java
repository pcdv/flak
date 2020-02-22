package flak.spi;

import java.lang.reflect.Method;

import flak.Request;

/**
 * @author pcdv
 */
public interface SPRequest extends Request {
  String[] getSplitUri();

  String getSplit(int tokenIndex);

  String getSplat(int tokenIndex);

  void setHandler(Method handler);
}
