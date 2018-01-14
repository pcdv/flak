package flak;

import java.lang.reflect.Method;

/**
 * @author pcdv
 */
public interface SuccessHandler {
  /**
   * Called when a route handler was successfully called.
   *
   * @param r the request
   * @param method the java method invoked by reflection
   * @param args the arguments passed to method
   * @param result the value returned by the handler method
   */
  void onSuccess(Request r, Method method, Object[] args, Object result);
}
