package flak.backend.jdk;

/**
 * @deprecated moved to {@link flak.spi.FormImpl}, which this only extends so
 * that code written for flak 2.x runs and compiles unchanged: import
 * flak.spi.FormImpl instead.
 */
@Deprecated
public class FormImpl extends flak.spi.FormImpl {

  /**
   * @deprecated see {@link flak.spi.FormImpl#FormImpl(String, boolean)}
   */
  @Deprecated
  public FormImpl(String data, boolean urlDecode) {
    super(data, urlDecode);
  }
}
