package flak;

/**
 * @author pcdv
 */
public abstract class Flak {
  public static AppFactory getFactory() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    return (AppFactory) Class.forName("net.jflask.JdkAppFactory")
                             .newInstance();
  }
}
