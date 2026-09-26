package com.github.pcdv.flak.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flak.App;
import flak.Form;
import flak.RouteParameter;
import flak.annotations.Route;
import flak.jackson.JSON;
import flak.jackson.JsonInputMapper;
import flak.jackson.JsonInputReader;
import flak.jackson.JsonOutputFormatter;
import flak.spi.AbstractApp;
import flak.spi.AbstractMethodHandler;
import flak.spi.FlakAnnotations;
import flak.spi.HandlerSpec;
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
 * Call {@link #scan(App)} to describe the route handlers of an app, or
 * {@link #scan(Class)} for those of a class, and
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
   * What the generator needs to know of a route handler, whether it comes
   * from an app or from a class.
   *
   * @param path       the path to document, variables in flak syntax
   * @param parameters what the parameters of the method are bound to
   * @param readsJson  whether the body is read as JSON
   * @param writesJson whether the response is written as JSON
   */
  private record Endpoint(String path,
                          String httpMethod,
                          Method method,
                          List<RouteParameter> parameters,
                          boolean readsJson,
                          boolean writesJson) {
  }

  /**
   * Describes the route handlers of specified app, at their full path, i.e.
   * prefixed with the path of the app. Static resources are left out.
   * <p>
   * Use {@link #scan(Class)} instead when the app serves several APIs, each
   * documented separately.
   */
  public OpenApiGenerator scan(App app) {
    // by class, so that the class tags come first
    Map<Class<?>, List<Endpoint>> byClass = new TreeMap<>(Comparator.comparing(Class::getName));

    ((AbstractApp) app).getMethodHandlers()
                       .filter(h -> !(h.getTarget() instanceof AbstractResourceHandler))
                       .forEach(h -> byClass.computeIfAbsent(h.getJavaMethod().getDeclaringClass(),
                                                             c -> new ArrayList<>())
                                            .add(endpoint(app, h)));

    byClass.forEach(this::scan);
    return this;
  }

  /**
   * Describes the @Route methods declared by specified class, at the path
   * given in their @Route: the generator does not know the app, nor the
   * prefix the class may be scanned with.
   * <p>
   * The parameters are bound as flak binds them, except that without the app
   * the custom extractors are unknown: a request body is only described for
   * handlers reading JSON, the parameter with @JSON or else the last one that
   * could be a body.
   */
  public OpenApiGenerator scan(Class<?> clazz) {
    List<Endpoint> endpoints = new ArrayList<>();
    for (Method m : clazz.getDeclaredMethods()) {
      Route route = m.getAnnotation(Route.class);
      if (route != null)
        endpoints.add(endpoint(FlakAnnotations.describe(route.value(), m)));
    }
    return scan(clazz, endpoints);
  }

  private OpenApiGenerator scan(Class<?> clazz, List<Endpoint> endpoints) {
    Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags
      = AnnotationsUtils.getTags(clazz.getAnnotationsByType(Tag.class), false);

    addTags(tags);

    // so that the output does not depend on the order of the handlers
    endpoints.stream()
             .sorted(Comparator.comparing((Endpoint e) -> e.method().getName())
                               .thenComparing(Endpoint::httpMethod))
             .forEach(e -> scanEndpoint(e, tags));
    return this;
  }

  private static Endpoint endpoint(App app, AbstractMethodHandler h) {
    return new Endpoint(app.getPath() + h.getRoute(),
                        h.getHttpMethod(),
                        h.getJavaMethod(),
                        h.getParameters(),
                        h.getInputParser() instanceof JsonInputReader
                          || h.getInputParser() instanceof JsonInputMapper,
                        h.getOutputFormatter() instanceof JsonOutputFormatter);
  }

  /**
   * Without the app, what reads and writes JSON is told by @JSON, on the
   * method, its class or a parameter, as flak-jackson does.
   */
  private static Endpoint endpoint(HandlerSpec spec) {
    Method m = spec.javaMethod();
    boolean writesJson = m.isAnnotationPresent(JSON.class)
      || m.getDeclaringClass().isAnnotationPresent(JSON.class);
    boolean readsJson = writesJson || spec.parameters()
                                          .stream()
                                          .anyMatch(p -> p.javaParameter().isAnnotationPresent(JSON.class));

    // a body can only be told from what a custom extractor provides by the
    // app, so only that of a JSON handler is kept, and if it has several
    // candidates, the one with @JSON, or else the last one
    List<RouteParameter> params = new ArrayList<>();
    RouteParameter body = null;
    for (RouteParameter p : spec.parameters()) {
      if (p.kind() != RouteParameter.Kind.BODY)
        params.add(p);
      else if (readsJson && (body == null || !body.javaParameter().isAnnotationPresent(JSON.class)))
        body = p;
    }
    if (body != null)
      params.add(body);

    return new Endpoint(spec.route(), spec.httpMethod(), m, params, readsJson, writesJson);
  }

  private void addTags(Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags) {
    tags.ifPresent(t -> t.forEach(this::addTag));
  }

  private void addTag(io.swagger.v3.oas.models.tags.Tag tag) {
    if (tagsByName.put(tag.getName(), tag) == null) {
      api.addTagsItem(tag);
    }
  }

  private void scanEndpoint(Endpoint e, Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags) {
    Method m = e.method();
    Optional<Set<io.swagger.v3.oas.models.tags.Tag>> methodTags
      = AnnotationsUtils.getTags(m.getAnnotationsByType(Tag.class), false);

    addTags(methodTags);
    if (methodTags.isPresent())
      tags = methodTags;

    scanSchema(m.getReturnType());

    Operation op = new Operation().operationId(m.getName());
    op.responses(scanResponses(e));
    scanParameters(e, op);

    io.swagger.v3.oas.annotations.Operation ope = m.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
    if (ope != null) {
      op.description(convertDesc(m.getDeclaringClass().getClassLoader(),
                                 ope.description())).summary(ope.summary());
    }

    op.requestBody(requestBody(e));

    String endpoint = convertPath(e.path());
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
    path.operation(PathItem.HttpMethod.valueOf(e.httpMethod()), op);
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

  private RequestBody requestBody(Endpoint e) {
    io.swagger.v3.oas.annotations.parameters.RequestBody reqBody
      = e.method().getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);

    if (reqBody == null) {
      return defaultRequestBody(e);
    }

    Content content = new Content();
    for (io.swagger.v3.oas.annotations.media.Content c : reqBody.content()) {
      fillContent(c, content, e.readsJson());
    }
    return new RequestBody()
      .content(content).description(reqBody.description()).required(reqBody.required());
  }

  /**
   * The body the handler reads, if any, with the schema of its type.
   */
  private RequestBody defaultRequestBody(Endpoint e) {
    RouteParameter body = e.parameters()
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
      new Content().addMediaType(e.readsJson() ? "application/json" : "*/*",
                                 new MediaType().schema(schema)));
  }

  private void fillContent(io.swagger.v3.oas.annotations.media.Content annContent,
                           Content content,
                           boolean json) {
    content.addMediaType
             (json ? "application/json" : annContent.mediaType(),
              new MediaType().schema(new Schema<>().$ref(annContent.schema().ref())));
  }

  private ApiResponses scanResponses(Endpoint e) {
    ApiResponses responses = scanDeclaredResponses(e);
    return responses == null ? buildDefaultResponses(e) : responses;
  }

  private ApiResponses buildDefaultResponses(Endpoint e) {
    Method m = e.method();

    // what flak answers when the handler returns, unless it sets another
    // status: @ApiResponse documents the others
    ApiResponse ok = new ApiResponse().description("OK");
    ApiResponses resp = new ApiResponses().addApiResponse("200", ok);

    if (m.getReturnType() == void.class)
      return resp;

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
    if (e.writesJson())
      content.addMediaType("application/json", mt);
    else
      content.addMediaType("*/*", mt);

    ok.content(content);
    return resp;
  }

  private ApiResponses scanDeclaredResponses(Endpoint e) {
    ApiResponses responses = new ApiResponses();
    for (io.swagger.v3.oas.annotations.responses.ApiResponse a
      : TypeUtil.getAnnotations(e.method(),
                                io.swagger.v3.oas.annotations.responses.ApiResponse.class,
                                io.swagger.v3.oas.annotations.responses.ApiResponses.class,
                                io.swagger.v3.oas.annotations.responses.ApiResponses::value)) {

      responses.addApiResponse(a.responseCode(), apiResponse(a, e.writesJson()));
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

  private void scanParameters(Endpoint e, Operation op) {

    for (RouteParameter p : e.parameters()) {
      io.swagger.v3.oas.annotations.Parameter ann = parameterAnnotation(p);
      if (ann != null && ann.hidden())
        continue;

      if (p.kind() == RouteParameter.Kind.QUERY) {
        Schema<?> schema = getSchemaForType(p.type());
        if (p.defaultValue() != null)
          schema.setDefault(p.type() == String[].class
                            ? Collections.singletonList(p.defaultValue())
                            : p.defaultValue());
        op.addParametersItem(annotate(new Parameter().in("query")
                                                     .name(p.name())
                                                     .description(p.description())
                                                     .required(p.required() ? true : null)
                                                     .schema(schema),
                                      p, ann));
      }
      // e.g. a header read from the Request: the annotation says it all
      else if (ann != null && p.kind() != RouteParameter.Kind.PATH)
        op.addParametersItem(annotate(null, null, ann));
    }

    for (io.swagger.v3.oas.annotations.Parameter ann
      : TypeUtil.getAnnotations(e.method(), io.swagger.v3.oas.annotations.Parameter.class,
                                Parameters.class, Parameters::value)) {
      op.addParametersItem(annotate(null, null, ann));
    }

    // complete with the variables of the route not declared on the method
    for (RouteParameter p : e.parameters()) {
      if (p.kind() == RouteParameter.Kind.PATH) {
        List<Parameter> parameters = op.getParameters();
        if (parameters == null || parameters.stream().noneMatch(d -> p.name().equals(d.getName()))) {
          op.addParametersItem(annotate(new PathParameter().name(p.name())
                                                           .schema(getSchemaForType(p.type())),
                                        p, parameterAnnotation(p)));
        }
      }
    }

  }

  private static io.swagger.v3.oas.annotations.Parameter parameterAnnotation(RouteParameter p) {
    return p.javaParameter().getAnnotation(io.swagger.v3.oas.annotations.Parameter.class);
  }

  /**
   * Completes a parameter with what a @Parameter declares, e.g. its
   * description. Its name, location and type need not be declared again
   * when flak knows them.
   *
   * @param param the parameter as flak binds it, null if flak does not know it
   * @param bound   what flak binds it to, null if flak does not know it
   */
  private Parameter annotate(Parameter param,
                             RouteParameter bound,
                             io.swagger.v3.oas.annotations.Parameter ann) {
    if (ann == null)
      return param;

    Type type = bound == null
      ? ParameterProcessor.getParameterType(ann, false)
      : bound.javaParameter().getParameterizedType();
    Schema<?> schema = param == null ? null : param.getSchema();

    Parameter res = ParameterProcessor.applyAnnotations(param, type, Collections.singletonList(ann),
                                                        api.getComponents(), null, null, null);

    // swagger derives a schema from the type, which knows less than flak,
    // e.g. the default value of a query parameter
    if (schema != null && !AnnotationsUtils.hasSchemaAnnotation(ann.schema()))
      res.setSchema(schema);
    return res;
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
