package flak.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultMapperProvider implements MapperProvider {
  private ObjectMapper mapper;

  public DefaultMapperProvider(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ObjectMapper getMapper(String id) {
    return mapper;
  }
}
