package flak.jackson;

import com.fasterxml.jackson.databind.ObjectReader;
import flak.InputParser;
import flak.Request;

/**
 * An input parser using an ObjectReader. This is the recommended way since ObjectReader
 * is immutable and thread safe.
 */
public class JsonInputReader<T> implements InputParser<T> {

  private final ObjectReader reader;

  public JsonInputReader(ObjectReader reader) {
    this.reader = reader;
  }

  @Override
  public T parse(Request req, Class<T> type) throws Exception {
    return reader.readValue(req.getInputStream());
  }
}
