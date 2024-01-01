package flak.spi;

import flak.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for handling compression of HTTP response data.
 */
public class CompressionHelper {

  public static final int COMPRESS_THRESHOLD = Integer.getInteger("flak.compressThreshold", 1024);

  /**
   * Attempts to compress the response output stream if compression is allowed,
   * the client supports gzip encoding, and content encoding is not already set.
   * If compression is applied, sets the Content-Encoding header to "gzip".
   *
   * @param resp The HTTP response to be compressed.
   * @return The output stream of the response (compressed if applicable).
   * @throws IOException If an I/O error occurs during compression setup.
   */
  public static OutputStream maybeCompress(Response resp) throws IOException {
    String acceptEncoding = resp.getRequest().getHeader("Accept-Encoding");
    if (resp.isCompressionAllowed()
      && acceptEncoding != null && acceptEncoding.contains("gzip")
      && !resp.hasResponseHeader("Content-Encoding")) {
      resp.addHeader("Content-Encoding", "gzip");
      ((SPResponse) resp).setOutputStream(new GZIPOutputStream(resp.getOutputStream()));
    }
    return resp.getOutputStream();
  }
}