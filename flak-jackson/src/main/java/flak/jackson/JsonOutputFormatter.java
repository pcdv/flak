package flak.jackson;

import com.fasterxml.jackson.databind.ObjectWriter;
import flak.OutputFormatter;
import flak.Response;

/**
 * An output formatter using an ObjectWriter, which is the recommended way of
 * generating JSON.
 * <p>
 * https://github.com/FasterXML/jackson-docs/wiki/Presentation:-Jackson-Performance
 * <p>
 * Historically we used an ObjectMapper, which caused performance issues as it is
 * stateful and synchronized. The first workaround was to create ObjectMapper objects
 * on-the-fly, which is costly.
 */
public class JsonOutputFormatter<T> implements OutputFormatter<T> {

  private final ObjectWriter writer;

  public JsonOutputFormatter(ObjectWriter writer) {
    this.writer = writer;
  }

  @Override
  public void convert(T data, Response resp) throws Exception {
    resp.addHeader("Content-Type", "application/json");
    resp.setStatus(200);
    writer.writeValue(resp.getOutputStream(), data);
  }
}
