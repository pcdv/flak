# JSON

`flak-jackson` converts handler arguments and return values from and to JSON
with [Jackson](https://github.com/FasterXML/jackson-databind). It is a
[plugin](plugins.md): adding the dependency is enough.

```groovy
implementation "com.github.pcdv.flak:flak-jackson:3.0"
```

## @JSON

[@JSON](../flak-jackson/src/main/java/flak/jackson/JSON.java) on a handler
converts what it returns to JSON, with `Content-Type: application/json`. A
parameter of an object type is parsed from the JSON body of the request:

```java
@Route("/api/items/:id")
@Put
@JSON
public Item update(String id, Item item) {
  return store.save(id, item);
}
```

- Its type tells Jackson what to build, which can be a class of your own, a
  `Map`, a `List`, a `JsonNode`… Put the body parameter last: Flak then
  builds a reader for its type once, rather than looking the type up on each
  request. Types of `java.lang` (e.g. `String`) and of Flak itself are not
  taken for a body.
- A handler can read JSON without returning JSON: put `@JSON` on the
  parameter rather than on the method.

  ```java
  @Route("/api/items")
  @Post
  public String create(@JSON Item item) {
    return store.add(item);
  }
  ```

- `@JSON(inputClass = Item.class)` states the type to parse, when it cannot
  be taken from the parameter.
- A `void` handler annotated with `@JSON` returns the JSON literal `null`.

The body is read like any other, so the [size limit](request-bodies.md#size-limit)
applies. JSON responses are compressed when
[compression](compression.md) is allowed.

## Configuring Jackson

By default, a plain `ObjectMapper` is used. To change its settings, register
your own as the default:

```java
ObjectMapper mapper = new ObjectMapper()
  .registerModule(new JavaTimeModule())
  .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

JacksonPlugin.get(app).registerMapper("default", mapper);
```

Handlers that need other settings can use a mapper of their own, registered
under another name and selected with `@JSON("name")`:

```java
JacksonPlugin.get(app).registerMapper("pretty",
  new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));

@Route("/api/debug")
@JSON("pretty")
public Map<String, Object> debug() { ... }
```

Register mappers before scanning the handlers that use them. Each handler
builds its `ObjectReader` and `ObjectWriter` once, when it is scanned; these
are immutable, so concurrent requests do not contend for them.

## Without the plugin

The plugin only saves some boilerplate. The same can be done with an output
formatter and an input parser (see [Responses](responses.md#output-formatters)
and [Handler arguments](arguments.md#objects-parsed-from-the-body)):

```java
app.addOutputFormatter("JSON", new JsonOutputFormatter<>(mapper.writer()));
app.addInputParser("JSON", new JsonInputReader<>(mapper.readerFor(Item.class)));

@Route("/api/items")
@Post
@InputFormat("JSON")
@OutputFormat("JSON")
public Item create(Item item) { ... }
```
