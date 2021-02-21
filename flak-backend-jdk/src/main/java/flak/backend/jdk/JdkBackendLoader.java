package flak.backend.jdk;

import flak.AppFactory;
import flak.FlakBackendLoader;

public class JdkBackendLoader implements FlakBackendLoader {
  @Override
  public Class<? extends AppFactory> getFactoryClass() {
    return JdkAppFactory.class;
  }

  @Override
  public AppFactory getFactory() {
    return new JdkAppFactory();
  }
}
