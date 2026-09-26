# OpenAPI

`flak-swagger` generates an [OpenAPI](https://www.openapis.org/)
specification from the route handlers of an application. What Flak knows,
such as routes, methods, parameters and JSON types, is filled in
automatically, as Flak binds them. The rest can be added with
[Swagger annotations](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations).

```groovy
implementation "com.github.pcdv.flak:flak-swagger:3.0"
```

## Generating a specification

```java
OpenApiGenerator gen = new OpenApiGenerator();
gen.setObjectMapper(mapper);          // the mapper used for JSON, if configured
gen.scan(app);

gen.getAPI().info(new Info().title("Shop API").version("1.0"));

String yaml = gen.toYaml();
String json = gen.toJSON();
```

`scan(app)` describes every route of the app, once they are all registered:
those added later are not described. When an app serves several APIs, each
documented on its own, scan the classes of one API instead:

```java
gen.scan(ItemRoutes.class);
gen.scan(OrderRoutes.class);
```

This describes the `@Route` methods declared by those classes, and nothing
else. Not knowing the app, it differs in three ways:

- paths are written as in `@Route`, without the path of the app or the
  prefix given to `scan(obj, prefix)`
- what is JSON is told by `@JSON`, on the method, its class or a parameter
- the custom extractors of the app are unknown, so a request body is only
  described for handlers reading JSON: the parameter with `@JSON`, or else
  the last parameter that could be a body

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

For each route handler, static resources excepted:

- **the path**, as the app serves it with `scan(app)`: prefixed with the
  path of the app and with the prefix given to `scan(obj, prefix)`, if any.
  Its variables are in
  OpenAPI syntax: `/items/:id` becomes `/items/{id}`, and so does a splat,
  `/files/*path` becoming `/files/{path}`.
- **the operation**, for its HTTP method, `@Head` included. Its id is the
  name of the Java method.
- **tags**: those of `@Tag` on the method, or else on the class, or else the
  simple name of the class
- **parameters**:
  - every variable of the route, as a path parameter of type `string` or
    `integer`
  - every [`@QueryParam`](arguments.md#query-parameters), with its type,
    default value and description
  - those declared with `@Parameter` on the method, which take precedence
    over a path variable of the same name

  A `@Parameter` on a parameter completes what Flak knows of it, without
  repeating its name, location or type, e.g.
  `@Parameter(required = true, example = "42") @QueryParam("id") String id`.
  `@Parameter(hidden = true)` leaves it out. A description alone is shorter
  with `@QueryParam(value = "id", description = "...")`.
- **the request body**: what `@RequestBody` declares, or else the schema of
  the parameter [parsed from the body](arguments.md#objects-parsed-from-the-body),
  as `application/json` when it is read as JSON. A `Form` is not described.
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

`setRemovePrefix("/api")` strips a common prefix from all paths, e.g. to
declare it once in `servers`.
