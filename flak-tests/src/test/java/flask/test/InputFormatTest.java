package flask.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import flak.annotations.InputFormat;
import flak.annotations.Post;
import flak.annotations.Route;
import flak.jackson.JsonInputMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * @author pcdv
 */
public class InputFormatTest extends AbstractAppTest {

  @Post
  @InputFormat("JSON")
  @Route("/pojo/:id")
  public String postJsonObject(String id, Pojo obj) {
    return obj.name + id;
  }

  public static class Pojo {
    public String name;
  }

  @Override
  protected void preScan() {
    app.addInputParser("JSON", new JsonInputMapper<>(id -> new ObjectMapper()));
  }

  @Test
  public void testPostPojo() throws IOException {
    Assert.assertEquals("foo42", client.post("/pojo/42", "{\"name\":\"foo\"}"));
  }
}
