package flask.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.pcdv.flak.swagger.OpenApiGenerator;
import flak.annotations.Route;
import flak.jackson.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

  @Route("/getTwo/:one/:two")
  @JSON
  @Parameter(name = "one", in = ParameterIn.QUERY, schema = @Schema(type = "string"))
  @Parameter(name = "two", in = ParameterIn.QUERY, example = "Foo")
  public void getTwoParam(String one, String two) {
  }

  @Route("/api")
  public String getAPI() {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(getClass());
    return gen.toYaml();
  }

  @Route("/api/json")
  public String getAPIJson() throws JsonProcessingException {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(getClass());
    return gen.toJSON();
  }

  public static class OtherHandler {
    @Route("/other")
    public void getOther() {
    }
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
    PathItem getTwoParam = api.getPaths().get("/getTwo/{one}/{two}");
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
    assertEquals("Foo", twoParams.get(1).getExample());

    // getOther()
    assertEquals("[OtherHandler]", getOther.getGet().getTags().toString());

    System.out.println(client.get("/api"));
    System.out.println(client.get("/api/json"));
  }
}
