package flak.spi;

import flak.Response;

import java.io.OutputStream;

public interface SPResponse extends Response {

  /**
   * Used when applying compression, to wrap the default output stream in a
   * {@link java.util.zip.GZIPOutputStream}.
   */
  void setOutputStream(OutputStream out);

  /**
   * Tells whether the output stream was already obtained, i.e. whether the
   * handler may have written something into the response.
   */
  boolean hasOutputStream();

  /**
   * Terminates the response abnormally, when a failure occurs after some of it
   * was already sent and the status can no longer be changed. The client must
   * see a broken response rather than a truncated but valid one.
   */
  void abort();

}
