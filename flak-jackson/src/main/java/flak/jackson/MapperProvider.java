package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides the ObjectMapper associated with an ID, i.e. with the value of
 * a {@link JSON} annotation. It is mostly asked once per handler, to build an
 * ObjectReader and an ObjectWriter, which are immutable and shared by all the
 * requests; only a body parsed without a known type asks for it on each
 * request.
 * <p>
 * Registering mappers with
 * {@link JacksonPlugin#registerMapper(String, ObjectMapper)} is simpler than
 * providing an implementation.
 */
public interface MapperProvider {

  /**
   * @param id the optional ID provided with the JSON annotation.
   * @see JSON
   */
  ObjectMapper getMapper(String id);
}
