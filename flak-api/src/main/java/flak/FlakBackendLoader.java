package flak;

import flak.AppFactory;

/**
 * Installs a Flak backend / AppFactory. Discovered using Java ServiceLoader.
 */
public interface FlakBackendLoader {
  Class<? extends AppFactory> getFactoryClass();

  AppFactory getFactory();
}
