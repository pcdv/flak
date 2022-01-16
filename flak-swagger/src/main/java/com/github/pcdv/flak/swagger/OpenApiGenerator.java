package com.github.pcdv.flak.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flak.annotations.Delete;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.Route;
import flak.jackson.JSON;
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
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Open Api Specification (OAS) generator.
 * It creates an OpenAPI object that can be obtained with getAPI() and extended (e.g.
 * info, servers, ...)
 * Call {@link #scan(Class)} to scan Flak handlers and populate API paths, and
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

  private HashMap<String, io.swagger.v3.oas.models.tags.Tag> tagsByName = new HashMap<>();
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
   * Sets a prefix that should removed from scanned endpoints when generating the API.
   */
  public void setRemovePrefix(String removePrefix) {
    this.removePrefix = removePrefix;
  }

  /**
   * Introspects specified class to populate paths.
   */
  public OpenApiGenerator scan(Class<?> clazz) {
    Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags
      = AnnotationsUtils.getTags(clazz.getAnnotationsByType(Tag.class), false);

    addTags(tags);

    Arrays.stream(clazz.getDeclaredMethods())
          .sorted(Comparator.comparing(Method::getName))
          .forEach(
            m -> {
              Route route = m.getAnnotation(Route.class);
              if (route != null)
                scanMethod(m, route, tags);
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

  private void scanMethod(Method m, Route route, Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags) {
    Optional<Set<io.swagger.v3.oas.models.tags.Tag>> methodTags
      = AnnotationsUtils.getTags(m.getAnnotationsByType(Tag.class), false);

    addTags(methodTags);
    if (methodTags.isPresent())
      tags = methodTags;

    scanSchema(m.getReturnType());

    Operation op = new Operation().operationId(m.getName());
    op.responses(scanResponses(m));
    scanParameters(m, op);

    io.swagger.v3.oas.annotations.Operation ope = m.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
    if (ope != null) {
      op.description(convertDesc(m.getDeclaringClass().getClassLoader(), ope.description())).summary(ope.summary());
    }

    if (m.isAnnotationPresent(Post.class) || m.isAnnotationPresent(Delete.class)
      || m.isAnnotationPresent(Put.class) || m.isAnnotationPresent(Patch.class)) {
      op.requestBody(requestBody(m));
    }

    String endpoint = convertPath(route.value());
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
    path.operation(TypeUtil.getHttpMethod(m), op);
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

  private RequestBody requestBody(Method m) {
    io.swagger.v3.oas.annotations.parameters.RequestBody reqBody
      = m.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);

    if (reqBody == null) {
      return defaultRequestBody(m);
    }

    Content content = new Content();
    for (io.swagger.v3.oas.annotations.media.Content c : reqBody.content()) {
      fillContent(c, content, m);
    }
    return new RequestBody()
      .content(content).description(reqBody.description()).required(reqBody.required());
  }

  /**
   * Guesses the request body by looking at method parameters and JSON annotation.
   */
  private RequestBody defaultRequestBody(Method m) {
    Class<?>[] params = m.getParameterTypes();
    if (params.length > 0 && m.getAnnotation(JSON.class) != null) {
      // right now, the "body" parameter needs to be last
      Class<?> lastParam = params[params.length - 1];
      if (!lastParam.getName().startsWith("java.")) {
        scanSchema(lastParam);
        String ref = "#/components/schemas/" + lastParam.getSimpleName();
        return new RequestBody().content
          (new Content().addMediaType("application/json",
                                      new MediaType().schema(new Schema<>().$ref(ref))));
      }
    }
    return null;
  }

  private void fillContent(io.swagger.v3.oas.annotations.media.Content annContent,
                           Content content,
                           Method m) {
    content.addMediaType
      (m.isAnnotationPresent(JSON.class) ? "application/json" : annContent.mediaType(),
       new MediaType().schema(new Schema<>().$ref(annContent.schema().ref())));
  }

  private ApiResponses scanResponses(Method m) {
    ApiResponses responses = scanDeclaredResponses(m);
    return responses == null ? buildDefaultResponses(m) : responses;
  }

  private ApiResponses buildDefaultResponses(Method m) {

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
    if (m.getAnnotation(JSON.class) != null)
      content.addMediaType("application/json", mt);
    else
      content.addMediaType("*/*", mt);

    ApiResponses resp = new ApiResponses();
    resp._default(new ApiResponse().content(content).description("Missing description."));
    return resp;
  }

  private ApiResponses scanDeclaredResponses(Method m) {
    ApiResponses responses = new ApiResponses();
    for (io.swagger.v3.oas.annotations.responses.ApiResponse a
      : TypeUtil.getAnnotations(m,
                                io.swagger.v3.oas.annotations.responses.ApiResponse.class,
                                io.swagger.v3.oas.annotations.responses.ApiResponses.class,
                                io.swagger.v3.oas.annotations.responses.ApiResponses::value)) {

      responses.addApiResponse(a.responseCode(), apiResponse(a, m));
    }

    return !responses.isEmpty() ? responses : null;
  }

  private ApiResponse apiResponse(io.swagger.v3.oas.annotations.responses.ApiResponse r,
                                  Method m) {
    ApiResponse resp = new ApiResponse();

    Content content = new Content();
    io.swagger.v3.oas.annotations.media.Content[] annContent = r.content();
    for (io.swagger.v3.oas.annotations.media.Content c : annContent) {
      fillContent(c, content, m);
    }
    resp.content(content);
    resp.description(r.description());
    return resp;
  }

  private void scanParameters(Method m, Operation op) {
    for (io.swagger.v3.oas.annotations.Parameter ann
      : TypeUtil.getAnnotations(m, io.swagger.v3.oas.annotations.Parameter.class,
                                Parameters.class, Parameters::value)) {
      Type type = ParameterProcessor.getParameterType(ann, false);

      op.addParametersItem
        (ParameterProcessor.applyAnnotations(null,
                                             type,
                                             Collections.singletonList(ann),
                                             api.getComponents(),
                                             null, null, null));
    }
  }

  private String convertPath(String endpoint) {
    if (removePrefix != null && endpoint.startsWith(removePrefix))
      endpoint = endpoint.substring(removePrefix.length());
    return endpoint.replaceAll(":([A-Za-z0-9_]+)", "{$1}");
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
