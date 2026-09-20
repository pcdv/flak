package flask.test;

import java.io.IOException;

import flak.annotations.Route;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Checks that route handlers can accept arbitrary argument types, provided an
 * extractor was registered with App.addCustomExtractor().
 */
public class CustomExtractorTest extends AbstractAppTest {

  /**
   * An arbitrary type that flak knows nothing about.
   */
  public static class Token {
    final String value;

    Token(String value) {
      this.value = value;
    }
  }

  @Override
  protected void preScan() {
    app.addCustomExtractor(Token.class,
                           req -> new Token(req.getHeader("X-Token")));
  }

  @Route("/token")
  public String getToken(Token token) {
    return "token=" + token.value;
  }

  @Route("/token/:name")
  public String getTokenAndPathArg(String name, Token token) {
    return "name=" + name + " token=" + token.value;
  }

  @Test
  public void extractorProvidesTheArgument() throws IOException {
    client.addHeader("X-Token", "abc");
    assertEquals("token=abc", client.get("/token"));
  }

  @Test
  public void extractorIsCalledForEachRequest() throws IOException {
    client.addHeader("X-Token", "abc");
    assertEquals("token=abc", client.get("/token"));

    client.addHeader("X-Token", "def");
    assertEquals("token=def", client.get("/token"));
  }

  @Test
  public void extractedArgumentCanBeMixedWithPathVariables() throws IOException {
    client.addHeader("X-Token", "abc");
    assertEquals("name=foo token=abc", client.get("/token/foo"));
  }
}
