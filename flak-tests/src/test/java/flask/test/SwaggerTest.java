package flask.test;

import com.github.pcdv.flak.swagger.OpenApiGenerator;
import flak.annotations.Route;
import flak.jackson.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwaggerTest extends AbstractAppTest {

  @Route("/data")
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

  @Test
  public void testSwagger() throws IOException {
    OpenApiGenerator gen = new OpenApiGenerator();
    gen.scan(getClass());
    OpenAPI api = gen.getAPI();

    Assert.assertEquals("Description of /data", api.getPaths().get("/data").getGet().getDescription());
    Assert.assertEquals("Summary of /data", api.getPaths().get("/data").getGet().getSummary());
    Assert.assertEquals(1, api.getPaths().get("/getOne/{one}").getGet().getParameters().size());
    List<io.swagger.v3.oas.models.parameters.Parameter> twoParams
      = api.getPaths().get("/getTwo/{one}/{two}").getGet().getParameters();
    Assert.assertEquals(2, twoParams.size());
    Assert.assertEquals("Foo", twoParams.get(1).getExample());

    // void => no response
    Assert.assertNull(api.getPaths().get("/getOne/{one}").getGet().getResponses());

    System.out.println(client.get("/api"));
  }
}
