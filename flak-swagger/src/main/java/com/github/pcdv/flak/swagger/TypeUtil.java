package com.github.pcdv.flak.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import flak.spi.AbstractMethodHandler;
import io.swagger.v3.oas.models.PathItem;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.function.Function;

public class TypeUtil {
  public static String convertReturnType(Method m) {
    Class<?> returnType = m.getReturnType();
    if (returnType == Integer.class || returnType == int.class)
      return "integer";
    else if (returnType == Boolean.class || returnType == boolean.class)
      return "boolean";
    else if (returnType == String.class)
      return "string";
    else if (returnType == JsonNode.class)
      return "object";
    else if (returnType == void.class)
      return "N/A";
    return null;
  }

  /**
   * The HTTP method of a route handler, decided exactly as flak does when it
   * binds the handler, @Head included.
   */
  public static PathItem.HttpMethod getHttpMethod(Method m) {
    return PathItem.HttpMethod.valueOf(AbstractMethodHandler.getHttpMethod(m));
  }

  /**
   * With repeatable annotations, these can be nested in an "array" annotation or not.
   * If there is only one un-nested annotation, the array annotation will be absent.
   * This method allows to easily access an array annotation, whatever the used syntax.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Annotation, U extends Annotation> T[] getAnnotations(Method cls, Class<T> single, Class<U> array, Function<U, T[]> getter) {
    U arr = cls.getAnnotation(array);
    if (arr != null)
      return getter.apply(arr);

    T a = cls.getAnnotation(single);
    if (a != null) {
      T[] res = (T[]) Array.newInstance(single, 1);
      res[0] = a;
      return res;
    }

    return (T[]) Array.newInstance(single, 0);
  }
}
