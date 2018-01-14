package flak;

/**
 * @author pcdv
 */
public abstract class ServerFactory {

  public abstract WebServer createServer();

  public static ServerFactory getInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    return (ServerFactory) Class.forName("net.jflask.JdkServerFactory")
                                .newInstance();
  }
}
