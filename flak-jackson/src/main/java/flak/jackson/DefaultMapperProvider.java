package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class DefaultMapperProvider implements MapperProvider {

  final Map<String, ObjectMapper> mappers = new HashMap<>();

  public DefaultMapperProvider(ObjectMapper mapper) {
    mappers.put("default", mapper);
  }

  @Override
  public ObjectMapper getMapper(String id) {
    if (id == null || id.isEmpty())
      id = "default";

    ObjectMapper m = mappers.get(id);
    if (m != null)
      return m;

    throw new IllegalStateException("Unknown JSON mapper ID: " + id + ". Call JacksonPlugin.setObjectMapperProvider() earlier");
  }

  public void registerMapper(String id, ObjectMapper mapper) {
    mappers.put(id, mapper);
  }
}
