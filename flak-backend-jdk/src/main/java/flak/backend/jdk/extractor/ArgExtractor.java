package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public abstract class ArgExtractor {

  protected int index;

  public ArgExtractor(int index) {
    this.index = index;
  }

  public abstract Object extract(JdkRequest request) throws Exception;
}
