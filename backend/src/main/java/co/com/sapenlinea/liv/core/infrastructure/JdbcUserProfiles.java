package co.com.sapenlinea.liv.core.infrastructure;

import co.com.sapenlinea.liv.core.application.UserProfiles;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the user and its administration in a read-only transaction: the transaction manager sets
 * the administration the security filter opened, so RLS shows only that administration's rows.
 */
@Component
class JdbcUserProfiles implements UserProfiles {

  private final JdbcClient jdbc;

  JdbcUserProfiles(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Profile> find(UUID userId, UUID administrationId) {
    return jdbc.sql(
            """
            SELECT u.full_name, u.email, a.name
              FROM core.admin_user u
              JOIN core.tax_administration a ON a.id = u.administration_id
             WHERE u.id = ? AND u.administration_id = ?
            """)
        .params(userId, administrationId)
        .query((row, number) ->
            new Profile(row.getString("full_name"), row.getString("email"), row.getString("name")))
        .optional();
  }
}
