package flask.test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import flak.App;
import flak.AppFactory;
import flak.annotations.Route;
import flask.test.util.SimpleClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pcdv
 */
public class SSLTest {

  private App app;

  @Route("/")
  public String test() {
    return "OK";
  }

  /**
   * NB: the keystore file has been generated with:
   * keytool -genkey -keyalg RSA -alias tomcat -keystore lig.keystore -validity 5000 -keysize 2048
   *
   * The test may fail after expiration of the certificate.
   */
  @Before
  public void setUp() throws Exception {
    SSLContext context = getSslContext("/test-resources/lig.keystore", "foobar");

    AppFactory factory = TestUtil.getFactory();
    factory.getServer().setSSLContext(context);
    factory.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

    app = factory.createApp();
    app.scan(this);
    app.start();
  }

  @After
  public void tearDown() {
    app.stop();
  }

  @Test
  public void testIt() throws Exception {
    // this test uses a self-signed certificate, so disable checks in client
    trustAllCertificates();
    Assert.assertTrue(app.getRootUrl().contains("https://"));
    Assert.assertEquals("OK", new SimpleClient(app.getRootUrl()).get("/"));
  }

  // https://stackoverflow.com/questions/2308479/simple-java-https-server
  @SuppressWarnings("SameParameterValue")
  private SSLContext getSslContext(String keyStorePath, String pwd) throws Exception {
    char[] password = pwd.toCharArray();

    SSLContext sslContext = SSLContext.getInstance("TLS");

    // initialise the keystore
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(getClass().getResourceAsStream(keyStorePath), password);

    // setup the key manager factory
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(keyStore, password);

    // setup the trust manager factory
    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(keyStore);

    // setup the HTTPS context and parameters
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    return sslContext;
  }

  // http://stacktips.com/snippet/how-to-trust-all-certificates-for-httpurlconnection-in-android
  private void trustAllCertificates() {
    try {
      TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }

          @Override
          public void checkClientTrusted(X509Certificate[] certs,
                                         String authType) {
          }

          @Override
          public void checkServerTrusted(X509Certificate[] certs,
                                         String authType) {
          }
        }
      };

      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier((arg0, arg1) -> true);
    }
    catch (Exception ignored) {
    }
  }
}
