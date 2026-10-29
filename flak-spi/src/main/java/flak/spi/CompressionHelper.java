package flak.spi;

import flak.App;
import flak.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
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
      SPResponse r = (SPResponse) resp;
      r.setOutputStream(gzip(r.getOutputStream(), r.getApp()));
    }
    return resp.getOutputStream();
  }

  /**
   * Wraps the output stream in a gzip one, with the level and buffer size of
   * the app.
   */
  static OutputStream gzip(OutputStream out, App app) throws IOException {
    int level = app.getCompressionLevel();
    return new GZIPOutputStream(out, app.getCompressionBufferSize()) {
      {
        if (level != Deflater.DEFAULT_COMPRESSION)
          def.setLevel(level);
      }
    };
  }
}