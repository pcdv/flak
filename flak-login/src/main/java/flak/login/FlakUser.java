package flak.login;

/**
 * @author pcdv
 */
public interface FlakUser {
  boolean hasPermission(String permission);

  String getId();
}
