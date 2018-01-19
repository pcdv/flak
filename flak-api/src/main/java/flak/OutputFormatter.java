package flak;

public interface OutputFormatter<T> {

  void convert(T data, Response resp) throws Exception;
}
