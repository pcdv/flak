# OpenAPI

`flak-swagger` generates an [OpenAPI](https://www.openapis.org/)
specification from the route handlers of an application. What Flak knows,
such as routes, methods, query parameters and JSON types, is filled in
automatically. The rest can be added with
[Swagger annotations](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations).

```groovy
implementation "com.github.pcdv.flak:flak-swagger:3.0"
```

## Generating a specification

```java
OpenApiGenerator gen = new OpenApiGenerator();
gen.setObjectMapper(mapper);          // the mapper used for JSON, if configured
gen.scan(ItemRoutes.class);
gen.scan(OrderRoutes.class);

gen.getAPI().info(new Info().title("Shop API").version("1.0"));

String yaml = gen.toYaml();
String json = gen.toJSON();
```

`getAPI()` returns the `OpenAPI` object of swagger-core, which can be
completed at will (info, servers, security schemes…) before it is written.

The specification can be served by the application itself:

```java
@Route("/api/openapi.yaml")
public String openApi() {
  return yaml;
}
```

## What is generated

For each `@Route` method of the scanned classes:

- **the path**, with variables in OpenAPI syntax: `/items/:id` becomes
  `/items/{id}`
- **the operation**, for its HTTP method, `@Head` included. Its id is the
  name of the Java method.
- **tags**: those of `@Tag` on the method, or else on the class, or else the
  simple name of the class
- **parameters**:
  - every variable of the route, as a path parameter
  - every [`@QueryParam`](arguments.md#query-parameters), with its type,
    default value and description
  - those declared with `@Parameter`
- **the request body** of a POST, PUT, PATCH or DELETE handler with `@JSON`
  on the method: the schema of its last parameter, or what `@RequestBody`
  declares
- **the response**: the schema of the return type, as `application/json`
  with `@JSON`. `@ApiResponse` annotations replace it, for instance to
  document several status codes. A `void` handler has none.
- **descriptions**, from `@Operation(summary = ..., description = ...)`. A
  description of the form `include:docs/items.md` is read from that
  classpath resource, so long descriptions can live in files.

Schemas are derived from the Java types with swagger-core. Call
`setObjectMapper()` before scanning, so that they follow the Jackson settings
of the application, e.g. ignored properties or naming strategies.
`scanSchema(type)` adds the schema of a type that no handler mentions
directly, such as a subtype listed in `@Schema(oneOf = ...)`.

## Limitations

- The generator scans classes, not apps. It does not know the path of the
  app, nor the prefix given to `scan(obj, prefix)`: routes appear as written
  in `@Route`. `setRemovePrefix("/api")` strips a common prefix from all
  paths, e.g. to declare it once in `servers`.
- Only `@JSON` handlers get a request body without annotations. Document the
  others with `@RequestBody`.
