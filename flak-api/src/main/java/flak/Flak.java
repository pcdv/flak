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
    factory.setPort(port);
    return factory.createApp();
  }

  /**
   * Creates a new instance of the default AppFactory.
   */
  public static AppFactory getFactory() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    return (AppFactory) Class.forName("flak.backend.jdk.JdkAppFactory")
                             .newInstance();
  }
}
