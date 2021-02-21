package flak;

import java.util.ServiceLoader;
import java.util.function.Predicate;

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
  public static AppFactory getFactory() {
    return getFactory(null);
  }

  public static AppFactory getFactory(Predicate<Class<? extends AppFactory>> factoryChooser) {
    int count = 0;

    for (FlakBackendLoader loader : ServiceLoader.load(FlakBackendLoader.class)) {
      count++;
      if (factoryChooser == null || factoryChooser.test(loader.getFactoryClass()))
        return loader.getFactory();
    }

    if (count == 0)
      throw new IllegalStateException("Found no backend. Please add flak-backend-jdk to your classpath");

    throw new IllegalStateException("No valid backend available");
  }
}
