package flak.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flak.Form;
import flak.HttpException;
import flak.InputParser;
import flak.Request;
import flak.annotations.QueryParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the arguments annotated with {@link QueryParams}. The query string
 * becomes a JSON object, with a string for each parameter, or an array of
 * strings for a repeated one, which Jackson then binds as it would bind a
 * body. So the properties are those Jackson finds, named as it names them,
 * e.g. after <code>@JsonProperty</code>, and their values converted as Jackson
 * converts strings.
 * <p>
 * As with <code>@QueryParam</code>, an empty value counts as absent, except
 * for a <code>String</code>, and a value that cannot be converted is rejected
 * with 400. So is the absence of a property that Jackson says is required,
 * e.g. with <code>@JsonProperty(required = true)</code>. Parameters matching
 * no property are ignored.
 *
 * @since 3.1.0
 */
public class JsonQueryReader implements InputParser<Object> {

  private final ObjectMapper mapper;

  private final ObjectReader reader;

  /**
   * The properties of each type, by name.
   */
  private final Map<Class<?>, Map<String, Property>> properties = new ConcurrentHashMap<>();

  /**
   * @param textual  whether an empty value is a value rather than an absent one
   * @param required whether the parameter must be given
   */
  private record Property(boolean textual, boolean required) {
  }

  /**
   * @param mapper the mapper whose settings bind the properties, e.g. their
   *               names, unchanged except that unknown properties are
   *               ignored and a single value fills a collection
   */
  public JsonQueryReader(ObjectMapper mapper) {
    this.mapper = mapper;
    this.reader = mapper.reader()
                        .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
  }

  @Override
  public Object parse(Request req, Class<Object> type) throws Exception {
    Map<String, Property> props = properties.computeIfAbsent(type, this::introspect);

    ObjectNode node = reader.getConfig().getNodeFactory().objectNode();
    for (Map.Entry<String, String> param : source(req).parameters()) {
      String name = param.getKey();
      String value = param.getValue();
      Property prop = props.get(name);
      if (value.isEmpty() && prop != null && !prop.textual())
        continue;

      JsonNode previous = node.get(name);
      if (previous == null)
        node.put(name, value);
      else if (previous.isArray())
        ((ArrayNode) previous).add(value);
      else
        node.set(name, node.arrayNode().add(previous).add(value));
    }

    props.forEach((name, prop) -> {
      if (prop.required() && !node.has(name))
        throw new HttpException(400, "Missing " + parameter() + " " + name);
    });

    try {
      return reader.forType(type).readValue(node);
    }
    catch (MismatchedInputException | ValueInstantiationException e) {
      throw new HttpException(400, message(e, node));
    }
  }

  /**
   * The mapper whose settings bind the properties, e.g. so that the OpenAPI
   * generator names and describes them as they are bound.
   *
   * @since 3.2.1
   */
  public ObjectMapper getMapper() {
    return mapper;
  }

  /**
   * Where the values are read from: the query string.
   *
   * @since 3.2.0
   */
  protected Form source(Request req) {
    return req.getQuery();
  }

  /**
   * What a value is called in error messages, e.g. "query parameter".
   *
   * @since 3.2.0
   */
  protected String parameter() {
    return "query parameter";
  }

  /**
   * What the values come from, in error messages, e.g. "query string".
   *
   * @since 3.2.0
   */
  protected String origin() {
    return "query string";
  }

  private Map<String, Property> introspect(Class<?> type) {
    DeserializationConfig config = reader.getConfig();
    BeanDescription desc = config.introspect(config.constructType(type));
    Map<String, Property> res = new HashMap<>();
    for (BeanPropertyDefinition p : desc.findProperties()) {
      if (p.couldDeserialize()) {
        Class<?> raw = p.getRawPrimaryType();
        res.put(p.getName(), new Property(raw == String.class || raw == Object.class,
                                          p.isRequired()));
      }
    }
    return res;
  }

  /**
   * Names the parameter that could not be bound, when Jackson tells which.
   */
  private String message(JsonMappingException e, ObjectNode node) {
    List<JsonMappingException.Reference> path = e.getPath();
    String name = path.isEmpty() ? null : path.get(0).getFieldName();
    if (name == null)
      return "Invalid " + origin() + ": " + e.getOriginalMessage();

    Object value = e instanceof InvalidFormatException
      ? ((InvalidFormatException) e).getValue()
      : node.get(name);
    if (value instanceof JsonNode && ((JsonNode) value).isTextual())
      value = ((JsonNode) value).asText();
    return "Invalid value for " + parameter() + " " + name + (value == null ? "" : ": " + value);
  }
}
