package flask.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.pcdv.flak.swagger.OpenApiGenerator;
import flak.annotations.Head;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.QueryParam;
import flak.annotations.Route;
import flak.jackson.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Tag(name = "Testing", description = "Test description")
public class SwaggerTest extends AbstractAppTest {

  @Route("/data")
  @Tag(name = "Data", description = "Data description")
  @Operation(description = "Description of /data", summary = "Summary of /data")
  @JSON
  public Map<String, String> getData() {
    return new HashMap<>();
  }

  @Route("/getOne/:one")
  @JSON
  @Parameter(name = "one", in = ParameterIn.PATH)
  public void getOneParam(String one) {
  }

  @Route("/getTwo")
  @JSON
  public void getTwoParam(@QueryParam(value = "one", description = "The first") String one,
                          @QueryParam(value = "two", description = "The second") int two) {
  }

  @Route("/getTwo2/:one/:two")
  @JSON
  public void getTwoParamNoDesc(String one, String two) {
  }

  @Route("/api")
  public String getAPI() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(app);
    return gen.toYaml();
  }

  @Route("/api/json")
  public String getAPIJson() throws JsonProcessingException {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(app);
    return gen.toJSON();
  }

  @Override
  protected void preScan() {
    app.scan(new OtherHandler());
    app.scan(new TypedHandler());
    app.scan(new ItemHandler(), "/v1");
    app.serveClasspath("/static", "/public");
  }

  public static class OtherHandler {
    @Route("/other")
    public void getOther() {
    }
  }

  public static class Item {
    public String name;
  }

  public static class ItemHandler {
    @Put
    @Route("/items/:id")
    @JSON
    public Item update(Item item, int id) {
      return item;
    }

    @Route("/files/*path")
    public String file(String path) {
      return path;
    }
  }

  /**
   * The routes are described as the app serves them: with the prefix they were
   * scanned with, and the body and variables flak binds.
   */
  @Test
  public void testRoutesAsServed() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(app);
    OpenAPI api = gen.getAPI();

    io.swagger.v3.oas.models.Operation update = api.getPaths().get("/v1/items/{id}").getPut();
    // the body is not the last parameter
    assertEquals("#/components/schemas/Item",
                 update.getRequestBody().getContent().get("application/json").getSchema().get$ref());
    assertNotNull(api.getComponents().getSchemas().get("Item"));

    io.swagger.v3.oas.models.parameters.Parameter id = update.getParameters().get(0);
    assertEquals("id", id.getName());
    assertEquals("path", id.getIn());
    assertEquals("integer", id.getSchema().getType());

    io.swagger.v3.oas.models.Operation file = api.getPaths().get("/v1/files/{path}").getGet();
    assertEquals("path", file.getParameters().get(0).getName());
    assertEquals("string", file.getParameters().get(0).getSchema().getType());

    // not part of the API
    assertTrue(api.getPaths().keySet().toString(),
               api.getPaths().keySet().stream().noneMatch(p -> p.startsWith("/static")));
  }

  public static class Token {
  }

  /**
   * Not scanned by the app: the custom extractor of Token is unknown to
   * scan(Class).
   */
  public static class TokenHandler {
    @Post
    @Route("/tokens/:id")
    @JSON
    public void save(Token token, int id, Item item) {
    }

    @Post
    @Route("/tokens/:id/check")
    public void check(Token token, int id) {
    }
  }

  /**
   * An app can serve several APIs, each documented from its own classes: only
   * the handlers of the scanned class are described, as written in @Route.
   */
  @Test
  public void testScanClass() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(ItemHandler.class);
    gen.scan(TokenHandler.class);
    OpenAPI api = gen.getAPI();

    assertEquals("[/files/{path}, /items/{id}, /tokens/{id}, /tokens/{id}/check]",
                 new TreeSet<>(api.getPaths().keySet()).toString());

    io.swagger.v3.oas.models.Operation update = api.getPaths().get("/items/{id}").getPut();
    assertEquals("#/components/schemas/Item",
                 update.getRequestBody().getContent().get("application/json").getSchema().get$ref());
    assertEquals("integer", update.getParameters().get(0).getSchema().getType());

    // the body is the last parameter that could be one
    assertEquals("#/components/schemas/Item",
                 api.getPaths().get("/tokens/{id}").getPost().getRequestBody().getContent()
                    .get("application/json").getSchema().get$ref());

    // no JSON, so what could be a body must come from a custom extractor
    assertNull(api.getPaths().get("/tokens/{id}/check").getPost().getRequestBody());
  }

  @Test
  public void testJsonOnClass() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(JsonTest.JsonRoutes.class);
    PathItem foo = gen.getAPI().getPaths().get("/classJson/foo");

    assertNotNull(foo.getGet().getResponses().get("200").getContent().get("application/json"));
    assertNotNull(foo.getPut().getRequestBody().getContent().get("application/json"));
  }

  public static class DescribedHandler {
    @Route("/described/:id")
    public void get(@Parameter(description = "The item") int id,
                    @Parameter(description = "Where to start", required = true, example = "0")
                    @QueryParam("from") String from,
                    @QueryParam(value = "to", description = "Where to stop") int to,
                    @QueryParam(value = "key", required = true) String key,
                    @Parameter(hidden = true) @QueryParam("debug") boolean debug,
                    @Parameter(description = "The color")
                    @QueryParam(value = "c", defaultValue = "RED") Color c) {
    }
  }

  /**
   * A @Parameter on a parameter completes what flak knows of it: its name,
   * location and type need not be repeated.
   */
  @Test
  public void testParameterAnnotations() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(DescribedHandler.class);

    Map<String, io.swagger.v3.oas.models.parameters.Parameter> params = new HashMap<>();
    gen.getAPI().getPaths().get("/described/{id}").getGet().getParameters()
       .forEach(p -> params.put(p.getName(), p));

    assertEquals("[c, from, id, key, to]", new TreeSet<>(params.keySet()).toString());

    io.swagger.v3.oas.models.parameters.Parameter id = params.get("id");
    assertEquals("path", id.getIn());
    assertEquals("The item", id.getDescription());
    assertEquals(Boolean.TRUE, id.getRequired());
    assertEquals("integer", id.getSchema().getType());

    io.swagger.v3.oas.models.parameters.Parameter from = params.get("from");
    assertEquals("query", from.getIn());
    assertEquals("Where to start", from.getDescription());
    assertEquals(Boolean.TRUE, from.getRequired());
    assertEquals("0", String.valueOf(from.getExample()));
    assertEquals("string", from.getSchema().getType());

    io.swagger.v3.oas.models.parameters.Parameter to = params.get("to");
    assertEquals("Where to stop", to.getDescription());
    assertEquals("integer", to.getSchema().getType());
    assertNull(to.getRequired());
    assertEquals(Boolean.TRUE, params.get("key").getRequired());

    // what flak knows is kept
    io.swagger.v3.oas.models.parameters.Parameter color = params.get("c");
    assertEquals("The color", color.getDescription());
    assertEquals("[RED, GREEN]", String.valueOf(color.getSchema().getEnum()));
    assertEquals("RED", color.getSchema().getDefault());
  }

  public enum Color {RED, GREEN}

  public static class TypedHandler {
    @Route("/typed")
    public void getTyped(@QueryParam("s") String[] s,
                         @QueryParam("i") int i,
                         @QueryParam(value = "l", defaultValue = "50") long l,
                         @QueryParam("d") Double d,
                         @QueryParam("b") boolean b,
                         @QueryParam("c") Color c) {
    }

    @Head
    @Route("/typed")
    public void headTyped() {
    }
  }

  @Test
  public void testQueryParamTypes() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(TypedHandler.class);
    PathItem typed = gen.getAPI().getPaths().get("/typed");

    Map<String, io.swagger.v3.oas.models.media.Schema<?>> schemas = new HashMap<>();
    typed.getGet().getParameters().forEach(p -> schemas.put(p.getName(), p.getSchema()));

    assertEquals("array", schemas.get("s").getType());
    assertEquals("string", ((ArraySchema) schemas.get("s")).getItems().getType());
    assertEquals("integer", schemas.get("i").getType());
    assertEquals("int32", schemas.get("i").getFormat());
    assertEquals("integer", schemas.get("l").getType());
    assertEquals("int64", schemas.get("l").getFormat());
    assertEquals(50L, ((Number) schemas.get("l").getDefault()).longValue());
    assertEquals("number", schemas.get("d").getType());
    assertEquals("boolean", schemas.get("b").getType());
    assertEquals("string", schemas.get("c").getType());
    assertEquals("[RED, GREEN]", String.valueOf(schemas.get("c").getEnum()));

    // not reported as a second GET
    assertNotNull(typed.getHead());
    assertEquals("headTyped", typed.getHead().getOperationId());
    assertEquals("getTyped", typed.getGet().getOperationId());
  }

  @Test
  public void testSwagger() throws IOException {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(getClass());
    gen.scan(OtherHandler.class);
    OpenAPI api = gen.getAPI();

    assertEquals(2, api.getTags().size());
    assertEquals("Test description", api.getTags().get(0).getDescription());
    assertEquals("Data description", api.getTags().get(1).getDescription());

    PathItem getData = api.getPaths().get("/data");
    PathItem getOneParam = api.getPaths().get("/getOne/{one}");
    PathItem getTwoParam = api.getPaths().get("/getTwo");
    PathItem getTwoParamNoDesc = api.getPaths().get("/getTwo2/{one}/{two}");
    PathItem getOther = api.getPaths().get("/other");

    // getData()
    assertEquals("Description of /data", getData.getGet().getDescription());
    assertEquals("Summary of /data", getData.getGet().getSummary());
    assertEquals("[Data]", getData.getGet().getTags().toString());

    // getOneParam()
    assertEquals(1, getOneParam.getGet().getParameters().size());
    assertEquals("one", getOneParam.getGet().getParameters().get(0).getName());
    // void => 200 with no content
    assertEquals("OK", getOneParam.getGet().getResponses().get("200").getDescription());
    assertNull(getOneParam.getGet().getResponses().get("200").getContent());
    assertEquals("OK", getData.getGet().getResponses().get("200").getDescription());
    assertNotNull(getData.getGet().getResponses().get("200").getContent().get("application/json"));
    assertEquals("[Testing]", getOneParam.getGet().getTags().toString());

    // getTwoParam()
    List<io.swagger.v3.oas.models.parameters.Parameter> twoParams = getTwoParam.getGet().getParameters();
    assertEquals(2, twoParams.size());
//    assertEquals("Foo", twoParams.get(1).getExample());

    twoParams = getTwoParamNoDesc.getGet().getParameters();
    assertEquals(2, twoParams.size());
    assertEquals("two", twoParams.get(1).getName());

    // getOther()
    assertEquals("[OtherHandler]", getOther.getGet().getTags().toString());

    System.out.println(client.get("/api"));
    System.out.println(client.get("/api/json"));
  }
}
