package flak;

public interface ResponseConverter<T> {

  void convert(T data, Response resp) throws Exception;
}
