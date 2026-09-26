package flask.test;

import flak.Request;
import flak.RouteParameter;
import flak.RouteParameter.Kind;
import flak.annotations.Post;
import flak.annotations.QueryParam;
import flak.annotations.Route;
import flak.jackson.JSON;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A route handler tells what each parameter of its method is bound to.
 */
public class RouteParametersTest extends AbstractAppTest {

  @Post
  @Route("/items/:id")
  @JSON
  public Map<String, Object> update(Request req,
                                    @QueryParam(value = "limit", defaultValue = "10",
                                      description = "At most") int limit,
                                    Map<String, Object> body,
                                    int id) {
    return body;
  }

  @Test
  public void testParameters() {
    List<RouteParameter> params = app.getHandler("POST", "/items/:id").getParameters();
    assertEquals(4, params.size());

    assertEquals(Kind.OTHER, params.get(0).kind());
    assertEquals(Request.class, params.get(0).type());
    assertNull(params.get(0).name());

    RouteParameter limit = params.get(1);
    assertEquals(Kind.QUERY, limit.kind());
    assertEquals("limit", limit.name());
    assertEquals(int.class, limit.type());
    assertEquals("10", limit.defaultValue());
    assertEquals("At most", limit.description());
    assertFalse(limit.required());

    assertEquals(Kind.BODY, params.get(2).kind());
    assertEquals(Map.class, params.get(2).type());

    RouteParameter id = params.get(3);
    assertEquals(Kind.PATH, id.kind());
    assertEquals("id", id.name());
    assertNull(id.defaultValue());
    assertTrue(id.required());
  }
}
