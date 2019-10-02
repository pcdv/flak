package flask.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import flak.Response;
import flak.annotations.Head;
import flak.annotations.Route;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author aramahay
 */
public class HeadTest extends AbstractAppTest {

  private List<LogRecord> logRecords;
  private Handler logListener;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    logRecords = new ArrayList<>();
    logListener = new Handler() {
      @Override
      public void publish(LogRecord record) {
        logRecords.add(record);
      }

      @Override public void flush() { }
      @Override public void close() throws SecurityException { }
    };

    Logger.getLogger("").addHandler(logListener);
  }

  @After
  public void tearDown() {
    super.tearDown();
    Logger.getLogger("").removeHandler(logListener);
  }


  @Head
  @Route(value = "/head/*path")
  public Response repositoryPathHead(String path) {
    int code = "ok".equals(path) ? 200 : 404;
    final Response response = app.getResponse();
    response.setStatus(code);
    return response;
  }


  @Test
  public void testHeadRequest()
  throws Exception {
    assertEquals(Collections.singletonList("HTTP/1.1 200 OK"), client.head("/head/ok").get(null));
    assertEquals(Collections.singletonList("HTTP/1.1 404 Not Found"), client.head("/head/nok").get(null));

    assertEquals(0, logRecords.size());
  }
}
