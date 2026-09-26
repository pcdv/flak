package com.github.pcdv.flak.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.Form;
import flak.RouteParameter;
import flak.jackson.JsonInputMapper;
import flak.jackson.JsonInputReader;
import flak.jackson.JsonOutputFormatter;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.resource.AbstractResourceHandler;
import flak.spi.util.IO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Open Api Specification (OAS) generator.
 * It creates an OpenAPI object that can be obtained with getAPI() and extended (e.g.
 * info, servers, ...)
 * Call {@link #scan(App)} to describe the route handlers of an app, and
 * {@link #scanSchema(Class)} to register schemas that may be missing in resources
 * (don't forget to call {@link #setObjectMapper(ObjectMapper)} before, so that all
 * Jackson settings are taken into account).
 */
public class OpenApiGenerator {

  private final OpenAPI api = new OpenAPI();

  /**
   * Allows stripping the beginning of endpoints.
   */
  private String removePrefix;

  private final HashMap<String, io.swagger.v3.oas.models.tags.Tag> tagsByName = new HashMap<>();
  private ObjectMapper objectMapper;

  public OpenApiGenerator() {
    api.components(new Components());
    api.paths(new Paths());
  }

  /**
   * Sets an object mapper that will be used when scanning classes to generate
   * schemas (allows ignoring methods ignored by Jackson). Call it before scanning
   * handlers or adding schemas.
   */
  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    ModelConverters.getInstance().addConverter(new ModelResolver(objectMapper));
  }

  /**
   * Sets a prefix that should be removed from scanned endpoints when generating the API.
   */
  public void setRemovePrefix(String removePrefix) {
    this.removePrefix = removePrefix;
  }

  /**
   * Describes the route handlers of specified app, at their full path, i.e.
   * prefixed with the path of the app. Static resources are left out.
   */
  public OpenApiGenerator scan(App app) {
    // by class, then by method name, so that the output does not depend on
    // the order in which the app holds its handlers
    Map<Class<?>, List<AbstractMethodHandler>> byClass =
      new TreeMap<>(Comparator.comparing(Class::getName));

    ((AbstractApp) app).getMethodHandlers()
                       .filter(h -> !(h.getTarget() instanceof AbstractResourceHandler))
                       .forEach(h -> byClass.computeIfAbsent(h.getJavaMethod().getDeclaringClass(),
                                                             c -> new ArrayList<>())
                                            .add(h));

    byClass.forEach((clazz, handlers) -> {
      Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags
        = AnnotationsUtils.getTags(clazz.getAnnotationsByType(Tag.class), false);

      addTags(tags);

      handlers.stream()
              .sorted(Comparator.comparing((AbstractMethodHandler h) -> h.getJavaMethod().getName())
                                .thenComparing(AbstractMethodHandler::getHttpMethod))
              .forEach(h -> scanHandler(app, h, tags));
    });
    return this;
  }

  private void addTags(Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags) {
    tags.ifPresent(t -> t.forEach(this::addTag));
  }

  private void addTag(io.swagger.v3.oas.models.tags.Tag tag) {
    if (tagsByName.put(tag.getName(), tag) == null) {
      api.addTagsItem(tag);
    }
  }

  private void scanHandler(App app,
                           AbstractMethodHandler h,
                           Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags) {
    Method m = h.getJavaMethod();
    Optional<Set<io.swagger.v3.oas.models.tags.Tag>> methodTags
      = AnnotationsUtils.getTags(m.getAnnotationsByType(Tag.class), false);

    addTags(methodTags);
    if (methodTags.isPresent())
      tags = methodTags;

    scanSchema(m.getReturnType());

    Operation op = new Operation().operationId(m.getName());
    op.responses(scanResponses(h));
    scanParameters(h, op);

    io.swagger.v3.oas.annotations.Operation ope = m.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
    if (ope != null) {
      op.description(convertDesc(m.getDeclaringClass().getClassLoader(),
                                 ope.description())).summary(ope.summary());
    }

    op.requestBody(requestBody(h));

    String endpoint = convertPath(app.getPath() + h.getRoute());
    PathItem path = api.getPaths().get(endpoint);
    if (path == null) {
      path = new PathItem();
      api.path(endpoint, path);
    }

    if (tags.isPresent()) {
      tags.get()
          .stream()
          .map(io.swagger.v3.oas.models.tags.Tag::getName)
          .forEach(op::addTagsItem);
    }
    else
      op.addTagsItem(m.getDeclaringClass().getSimpleName());
    path.operation(PathItem.HttpMethod.valueOf(h.getHttpMethod()), op);
  }

  private static String convertDesc(ClassLoader loader, String description) {
    if (description.startsWith("include:")) {
      try {
        InputStream in = loader.getResourceAsStream(description.substring("include:".length()));
        if (in != null)
          return new String(IO.readFully(in));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return description;
  }

  private RequestBody requestBody(AbstractMethodHandler h) {
    io.swagger.v3.oas.annotations.parameters.RequestBody reqBody
      = h.getJavaMethod().getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);

    if (reqBody == null) {
      return defaultRequestBody(h);
    }

    Content content = new Content();
    for (io.swagger.v3.oas.annotations.media.Content c : reqBody.content()) {
      fillContent(c, content, readsJson(h));
    }
    return new RequestBody()
      .content(content).description(reqBody.description()).required(reqBody.required());
  }

  /**
   * The body the handler reads, if any, with the schema of its type.
   */
  private RequestBody defaultRequestBody(AbstractMethodHandler h) {
    RouteParameter body = h.getParameters()
                           .stream()
                           .filter(p -> p.kind() == RouteParameter.Kind.BODY)
                           .findFirst()
                           .orElse(null);

    // NB: forms are not described
    if (body == null || body.type() == Form.class)
      return null;

    Class<?> type = body.type();
    Schema<?> schema = new Schema<>();
    if (!type.getName().startsWith("java.")) {
      scanSchema(type);
      schema.$ref("#/components/schemas/" + type.getSimpleName());
    }

    return new RequestBody().content(
      new Content().addMediaType(readsJson(h) ? "application/json" : "*/*",
                                 new MediaType().schema(schema)));
  }

  private static boolean readsJson(AbstractMethodHandler h) {
    return h.getInputParser() instanceof JsonInputReader
      || h.getInputParser() instanceof JsonInputMapper;
  }

  private static boolean writesJson(AbstractMethodHandler h) {
    return h.getOutputFormatter() instanceof JsonOutputFormatter;
  }

  private void fillContent(io.swagger.v3.oas.annotations.media.Content annContent,
                           Content content,
                           boolean json) {
    content.addMediaType
             (json ? "application/json" : annContent.mediaType(),
              new MediaType().schema(new Schema<>().$ref(annContent.schema().ref())));
  }

  private ApiResponses scanResponses(AbstractMethodHandler h) {
    ApiResponses responses = scanDeclaredResponses(h);
    return responses == null ? buildDefaultResponses(h) : responses;
  }

  private ApiResponses buildDefaultResponses(AbstractMethodHandler h) {
    Method m = h.getJavaMethod();

    if (m.getReturnType() == void.class)
      return null;

    MediaType mt = new MediaType();

    String type = TypeUtil.convertReturnType(m);
    if (type != null) {
      mt.schema(new Schema<>().type(type));
    }
    else {
      String name = m.getReturnType().getSimpleName();
      Map<String, Schema> schemas = api.getComponents().getSchemas();
      if (schemas != null && schemas.containsKey(name))
        mt.schema(new Schema<>().$ref("#/components/schemas/" + name));
    }

    Content content = new Content();
    if (writesJson(h))
      content.addMediaType("application/json", mt);
    else
      content.addMediaType("*/*", mt);

    ApiResponses resp = new ApiResponses();
    resp._default(new ApiResponse().content(content).description("Missing description."));
    return resp;
  }

  private ApiResponses scanDeclaredResponses(AbstractMethodHandler h) {
    ApiResponses responses = new ApiResponses();
    for (io.swagger.v3.oas.annotations.responses.ApiResponse a
      : TypeUtil.getAnnotations(h.getJavaMethod(),
                                io.swagger.v3.oas.annotations.responses.ApiResponse.class,
                                io.swagger.v3.oas.annotations.responses.ApiResponses.class,
                                io.swagger.v3.oas.annotations.responses.ApiResponses::value)) {

      responses.addApiResponse(a.responseCode(), apiResponse(a, writesJson(h)));
    }

    return !responses.isEmpty() ? responses : null;
  }

  private ApiResponse apiResponse(io.swagger.v3.oas.annotations.responses.ApiResponse r,
                                  boolean json) {
    ApiResponse resp = new ApiResponse();

    Content content = new Content();
    io.swagger.v3.oas.annotations.media.Content[] annContent = r.content();
    for (io.swagger.v3.oas.annotations.media.Content c : annContent) {
      fillContent(c, content, json);
    }
    resp.content(content);
    resp.description(r.description());
    return resp;
  }

  private void scanParameters(AbstractMethodHandler h, Operation op) {

    for (RouteParameter p : h.getParameters()) {
      if (p.kind() == RouteParameter.Kind.QUERY) {
        Schema<?> schema = getSchemaForType(p.type());
        if (p.defaultValue() != null)
          schema.setDefault(p.type() == String[].class
                            ? Collections.singletonList(p.defaultValue())
                            : p.defaultValue());
        op.addParametersItem(new Parameter().in("query").name(p.name()).description(p.description()).schema(schema));
      }
    }

    for (io.swagger.v3.oas.annotations.Parameter ann
      : TypeUtil.getAnnotations(h.getJavaMethod(), io.swagger.v3.oas.annotations.Parameter.class,
                                Parameters.class, Parameters::value)) {
      Type type = ParameterProcessor.getParameterType(ann, false);

      Parameter param = ParameterProcessor.applyAnnotations(
        null, type, Collections.singletonList(ann), api.getComponents(),
        null, null, null
      );
      op.addParametersItem(param);
    }

    // complete with the variables of the route not declared with @Parameter
    for (RouteParameter p : h.getParameters()) {
      if (p.kind() == RouteParameter.Kind.PATH) {
        List<Parameter> parameters = op.getParameters();
        if (parameters == null || parameters.stream().noneMatch(d -> p.name().equals(d.getName()))) {
          op.addParametersItem(new PathParameter().name(p.name()).schema(getSchemaForType(p.type())));
        }
      }
    }

  }

  /**
   * The schema of a query or path parameter, for each type that flak
   * supports there.
   */
  private static Schema<?> getSchemaForType(Class<?> type) {
    if (type == String.class)
      return new StringSchema();
    if (type == String[].class)
      return new ArraySchema().items(new StringSchema());
    if (type == Integer.class || type == int.class)
      return new IntegerSchema();
    if (type == Long.class || type == long.class)
      return new IntegerSchema().format("int64");
    if (type == Double.class || type == double.class)
      return new NumberSchema().format("double");
    if (type == Boolean.class || type == boolean.class)
      return new BooleanSchema();
    if (type.isEnum()) {
      StringSchema s = new StringSchema();
      for (Object constant : type.getEnumConstants())
        s.addEnumItem(((Enum<?>) constant).name());
      return s;
    }
    // not a type flak accepts, the app would fail to start anyway
    return new Schema<>();
  }

  /**
   * Converts the variables of a route to OpenAPI syntax: /items/:id becomes
   * /items/{id}, and so does a splat, e.g. /files/*path.
   */
  private String convertPath(String endpoint) {
    if (removePrefix != null && endpoint.startsWith(removePrefix))
      endpoint = endpoint.substring(removePrefix.length());
    return endpoint.replaceAll("[:*]([A-Za-z0-9_]+)", "{$1}");
  }

  /**
   * Adds a schema for specified type and/or referenced types (including oneOf)
   *
   * @see io.swagger.v3.oas.annotations.media.Schema#oneOf()
   */
  public void scanSchema(Class<?> type) {
    io.swagger.v3.oas.annotations.media.Schema ann = type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
    if (ann != null) {
      for (Class<?> cls : ann.oneOf()) {
        scanSchema(cls);
      }
    }
    ModelConverters.getInstance()
                   .read(type).forEach((n, s) -> api.getComponents().addSchemas(n, s));
  }

  public String toYaml() {
    ObjectMapper mapper = Yaml.mapper();

    try {
      mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(api);
    }
    catch (JsonProcessingException e) {
      e.printStackTrace();
      return null;
    }
  }

  public String toJSON() throws JsonProcessingException {
    ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
    mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(api);
  }

  public OpenAPI getAPI() {
    return api;
  }
}
