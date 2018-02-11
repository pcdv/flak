package flak.backend.jdk.extractor;

import java.util.Objects;

import flak.InputParser;
import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * @author pcdv
 */
public class ParsedInputExtractor extends ArgExtractor<Object> {
  private InputParser inputParser;

  private Class type;

  public ParsedInputExtractor(int index, InputParser inputParser, Class type) {
    super(index);
    this.inputParser = Objects.requireNonNull(inputParser);
    this.type = type;
  }

  @Override
  public Object extract(SPRequest request) throws Exception {
    return inputParser.parse(request, type);
  }
}
