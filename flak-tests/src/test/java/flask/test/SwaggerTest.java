package flask.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.pcdv.flak.swagger.OpenApiGenerator;
import flak.annotations.Head;
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
    gen.scan(app);
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
    gen.scan(app);
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
    assertNull(getOneParam.getGet().getResponses()); // void => no response
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
