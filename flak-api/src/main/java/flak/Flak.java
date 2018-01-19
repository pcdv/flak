package flak;

/**
 * @author pcdv
 */
public abstract class Flak {
  /**
   * Short for
   * <pre>
   * AppFactory factory = Flak.getFactory();
   * factory.setHttpPort(port);
   * app = factory.createApp();
   * </pre>
   */
  public static App createHttpApp(int port) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
    AppFactory factory = Flak.getFactory();
    factory.setHttpPort(port);
    return factory.createApp();
  }

  public static AppFactory getFactory() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    return (AppFactory) Class.forName("net.jflask.JdkAppFactory").newInstance();
  }
}
