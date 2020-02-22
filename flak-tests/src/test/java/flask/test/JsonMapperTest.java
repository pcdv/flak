package flask.test;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import flak.annotations.Route;
import flak.jackson.JSON;
import flak.jackson.JacksonPlugin;
import org.junit.Assert;
import org.junit.Test;

/**
 * Check that the ID passed in @JSON annotation can be used to tweak the
 * ObjectMapper used to serialize data.
 */
public class JsonMapperTest extends AbstractAppTest {

  interface Obj {
    String getValue();
  }

  static class ObjImpl implements Obj {
    @Override
    public String getValue() {
      return "foo";
    }
  }

  static class ObjSerializer extends JsonSerializer<Obj> {
    @Override
    public void serialize(Obj value,
                          JsonGenerator gen,
                          SerializerProvider serializers) throws IOException {
      gen.writeString("CUSTOM");
    }
  }

  @Route("/a")
  @JSON("A")

  public Obj getA() {
    return new ObjImpl();
  }

  @Route("/b")
  @JSON("B")
  public Obj getB() {
    return new ObjImpl();
  }

  @Override
  protected void preScan() {
    JacksonPlugin jp = app.getPlugin(JacksonPlugin.class);

    ObjectMapper a = new ObjectMapper();
    SimpleModule m = new SimpleModule();
    m.addSerializer(Obj.class, new ObjSerializer());
    a.registerModule(m);

    ObjectMapper b = new ObjectMapper();
    jp.setObjectMapperProvider(id -> {
      switch (id) {
      case "A":
        return a;
      case "B":
        return b;
      }
      return null;
    });
  }

  @Test
  public void testMapperProvider() throws Exception {
    Assert.assertEquals("{\"value\":\"foo\"}", client.get("/b"));
    Assert.assertEquals("\"CUSTOM\"", client.get("/a"));
  }
}
