package flak.backend.jdk.extractor;

import java.util.Objects;

import flak.InputParser;
import flak.backend.jdk.JdkRequest;

/**
 * @author pcdv
 */
public class InputExtractor extends ArgExtractor {
  private InputParser inputParser;

  private Class type;

  public InputExtractor(int index, InputParser inputParser, Class type) {
    super(index);
    this.inputParser = Objects.requireNonNull(inputParser);
    this.type = type;
  }

  @Override
  public Object extract(JdkRequest request) throws Exception {
    return inputParser.parse(request, type);
  }
}
