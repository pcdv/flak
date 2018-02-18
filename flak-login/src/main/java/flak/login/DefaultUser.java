package flak.login;

import java.util.HashSet;

/**
 * @author pcdv
 */
public class DefaultUser implements FlakUser {
  private final String id;

  private final HashSet<String> permissions = new HashSet<>();

  public DefaultUser(String id) {
    this.id = id;
  }

  @Override
  public boolean hasPermission(String permission) {
    return permissions.contains(permission);
  }

  @Override
  public String getId() {
    return id;
  }

  public void addPermission(String name) {
    permissions.add(name);
  }
}
