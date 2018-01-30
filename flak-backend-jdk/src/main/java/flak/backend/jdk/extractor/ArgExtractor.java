package flak.backend.jdk.extractor;

import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public abstract class ArgExtractor<T> {

  protected int index;

  public ArgExtractor(int index) {
    this.index = index;
  }

  public abstract T extract(JdkRequest request) throws Exception;
}
