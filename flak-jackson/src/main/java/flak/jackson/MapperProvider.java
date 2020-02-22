package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Providers an ObjectMapper for a given request. By default, a new mapper is
 * created for each request in order to execute concurrent requests without
 * contention.
 */
public interface MapperProvider {

  /**
   * @param id the optional ID provided with the JSON annotation.
   * @see JSON
   */
  ObjectMapper getMapper(String id);
}
