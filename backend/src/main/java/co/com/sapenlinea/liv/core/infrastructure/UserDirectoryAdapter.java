package co.com.sapenlinea.liv.core.infrastructure;

import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.UserDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Implements the {@link UserDirectory} port of {@code platform} (ADR 0025) with {@code
 * core.find_user_by_identity}: the only way {@code liv_app} reads a user without a tenant context,
 * and it returns only id, administration and state (plan, migrations).
 */
@Component
class UserDirectoryAdapter implements UserDirectory {

  private static final String ACTIVE = "ACTIVE";

  private final JdbcClient jdbc;

  UserDirectoryAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DirectoryUser> resolve(String issuer, String subject) {
    return jdbc.sql(
            "SELECT user_id, administration_id, status FROM core.find_user_by_identity(?, ?)")
        .params(issuer, subject)
        .query((row, number) ->
            new DirectoryUser(
                row.getObject("user_id", UUID.class),
                row.getObject("administration_id", UUID.class),
                ACTIVE.equals(row.getString("status"))))
        .optional();
  }
}
