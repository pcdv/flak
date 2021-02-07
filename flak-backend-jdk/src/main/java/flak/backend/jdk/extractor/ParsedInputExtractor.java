package flak.backend.jdk.extractor;

import java.util.Objects;

import flak.InputParser;
import flak.spi.ArgExtractor;
import flak.spi.SPRequest;

/**
 * Extracts a method argument using an InputParser.
 *
 * @author pcdv
 */
public class ParsedInputExtractor<T> extends ArgExtractor<T> {
  private final InputParser<T> inputParser;

  private final Class<T> type;

  public ParsedInputExtractor(int index, InputParser<T> inputParser, Class<T> type) {
    super(index);
    this.inputParser = Objects.requireNonNull(inputParser);
    this.type = type;
  }

  @Override
  public T extract(SPRequest request) throws Exception {
    return inputParser.parse(request, type);
  }
}
