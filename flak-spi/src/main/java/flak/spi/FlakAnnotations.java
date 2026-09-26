package flak.spi;

import flak.InputParser;
import flak.OutputFormatter;
import flak.Query;
import flak.Request;
import flak.Response;
import flak.RouteParameter;
import flak.RouteParameter.Kind;
import flak.annotations.Compress;
import flak.annotations.Delete;
import flak.annotations.Head;
import flak.annotations.InputFormat;
import flak.annotations.MaxBodySize;
import flak.annotations.Options;
import flak.annotations.OutputFormat;
import flak.annotations.Patch;
import flak.annotations.Post;
import flak.annotations.Put;
import flak.annotations.QueryParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the annotations of a route handler method: the only place where
 * Flak's own annotations are interpreted. Plugins read theirs, e.g. @JSON.
 */
final class FlakAnnotations {

  private FlakAnnotations() {
  }

  /**
   * @param route the route, relative to the app, e.g. "/items/:id"
   */
  static HandlerSpec read(AbstractApp app, String route, Method m) {
    return new HandlerSpec(route,
                           httpMethod(m),
                           m,
                           parameters(app, route, m),
                           outputFormatter(app, m),
                           inputParser(app, m),
                           m.isAnnotationPresent(Compress.class)
                             || m.getDeclaringClass().isAnnotationPresent(Compress.class),
                           maxBodySize(m));
  }

  private static String httpMethod(Method m) {
    if (m.isAnnotationPresent(Post.class))
      return "POST";
    if (m.isAnnotationPresent(Put.class))
      return "PUT";
    if (m.isAnnotationPresent(Patch.class))
      return "PATCH";
    if (m.isAnnotationPresent(Head.class))
      return "HEAD";
    if (m.isAnnotationPresent(Delete.class))
      return "DELETE";
    if (m.isAnnotationPresent(Options.class))
      return "OPTIONS";
    return "GET";
  }

  /**
   * Binds the parameters: those with a custom extractor, then @QueryParam,
   * then by type. A String or int is the next variable of the route, anything
   * not recognized is the body.
   */
  private static List<RouteParameter> parameters(AbstractApp app, String route, Method m) {
    List<String> variables = variables(route);
    List<RouteParameter> res = new ArrayList<>();
    int bound = 0; // the variables bound so far

    for (Parameter p : m.getParameters()) {
      Class<?> type = p.getType();
      QueryParam query = p.getAnnotation(QueryParam.class);

      if (app.getCustomExtractor(m, type) != null)
        res.add(new RouteParameter(Kind.OTHER, null, p, null, null));
      else if (query != null)
        res.add(new RouteParameter(Kind.QUERY,
                                   query.value(),
                                   p,
                                   QueryParam.NO_DEFAULT.equals(query.defaultValue())
                                     ? null
                                     : query.defaultValue(),
                                   query.description().isEmpty() ? null : query.description()));
      else if (type == Request.class || type == Response.class || type == Query.class)
        res.add(new RouteParameter(Kind.OTHER, null, p, null, null));
      else if (type == String.class || type == int.class) {
        if (bound >= variables.size())
          throw new IllegalArgumentException("Too many method parameters");
        res.add(new RouteParameter(Kind.PATH, variables.get(bound++), p, null, null));
      }
      else
        // a Form, or whatever the input parser reads
        res.add(new RouteParameter(Kind.BODY, null, p, null, null));
    }

    if (bound < variables.size())
      throw new IllegalArgumentException("Not enough method parameters");

    return res;
  }

  /**
   * The names of the variables of a route, e.g. [id, path] for
   * "/items/:id/*path".
   */
  static List<String> variables(String route) {
    List<String> res = new ArrayList<>();
    for (String token : route.split("/")) {
      if (token.startsWith(":") || token.startsWith("*"))
        res.add(token.substring(1));
    }
    return res;
  }

  private static OutputFormatter<?> outputFormatter(AbstractApp app, Method m) {
    OutputFormat output = m.getAnnotation(OutputFormat.class);
    if (output == null)
      return null;

    OutputFormatter<?> format = app.getOutputFormatter(output.value());
    if (format == null)
      throw new IllegalArgumentException("In method " + m.getName() + ": unknown output format: "
                                         + output.value());
    return format;
  }

  private static InputParser<?> inputParser(AbstractApp app, Method m) {
    InputFormat input = m.getAnnotation(InputFormat.class);
    return input == null ? null : app.getInputParser(input.value());
  }

  private static Long maxBodySize(Method m) {
    MaxBodySize a = m.getAnnotation(MaxBodySize.class);
    if (a == null)
      a = m.getDeclaringClass().getAnnotation(MaxBodySize.class);
    return a == null ? null : a.value();
  }
}
