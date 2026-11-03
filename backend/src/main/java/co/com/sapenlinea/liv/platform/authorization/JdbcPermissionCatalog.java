package co.com.sapenlinea.liv.platform.authorization;

import co.com.sapenlinea.liv.platform.PermissionCatalog;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The catalog of {@code platform.app_role} and {@code platform.role_permission} (migration
 * V202611010910). It is national, not per administration: no RLS context is needed to read it.
 */
@Component
class JdbcPermissionCatalog implements PermissionCatalog {

  private final JdbcClient jdbc;

  JdbcPermissionCatalog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<String> knownRoles(Set<String> roles) {
    if (roles.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(
        jdbc.sql("SELECT code FROM platform.app_role WHERE code = ANY (?)")
            .param(roles.toArray(String[]::new))
            .query(String.class)
            .list());
  }

  @Override
  public Set<String> permissionsOf(Set<String> roles) {
    if (roles.isEmpty()) {
      return Set.of();
    }
    return jdbc
        .sql(
            "SELECT DISTINCT permission_code FROM platform.role_permission WHERE role_code = ANY (?)")
        .param(roles.toArray(String[]::new))
        .query(String.class)
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableSet());
  }
}
