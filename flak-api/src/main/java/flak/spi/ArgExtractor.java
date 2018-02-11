package flak.spi;

/**
 * @author pcdv
 */
public abstract class ArgExtractor<T> {

  protected int index;

  public ArgExtractor(int index) {
    this.index = index;
  }

  public abstract T extract(SPRequest request) throws Exception;
}
