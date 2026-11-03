package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who the user of the request is (001-13), from the server only: the LIV registry for the user and
 * its administration — read under RLS with the registered administration the security filter opened
 * (001-17) — and the roles of the token that LIV knows, with their permissions. Nothing comes from
 * what the browser sends.
 */
@Service
public class GetCurrentUser {

  private final JdbcClient jdbc;
  private final PermissionCatalog permissions;

  GetCurrentUser(JdbcClient jdbc, PermissionCatalog permissions) {
    this.jdbc = jdbc;
    this.permissions = permissions;
  }

  /**
   * The user of the session.
   *
   * @param id the LIV id of the user
   * @param fullName its name in the registry
   * @param email its email in the registry
   * @param administrationId its registered administration
   * @param administrationName the name of that administration
   * @param roles the roles of the token that LIV knows
   * @param permissions the permissions of those roles
   */
  public record Me(
      UUID id,
      String fullName,
      String email,
      UUID administrationId,
      String administrationName,
      Set<String> roles,
      Set<String> permissions) {}

  @Transactional(readOnly = true)
  public Me get() {
    CurrentUser user =
        CurrentUser.current()
            .orElseThrow(() ->
                new AuthenticationRejectedException(
                    ErrorCodes.NOT_AUTHENTICATED, "No current user"));
    Set<String> roles = permissions.knownRoles(user.roles());
    return jdbc.sql(
            """
            SELECT u.full_name, u.email, a.name
              FROM core.admin_user u
              JOIN core.tax_administration a ON a.id = u.administration_id
             WHERE u.id = ? AND u.administration_id = ?
            """)
        .params(user.userId(), user.administrationId())
        .query((row, number) ->
            new Me(
                user.userId(),
                row.getString("full_name"),
                row.getString("email"),
                user.administrationId(),
                row.getString("name"),
                roles,
                permissions.permissionsOf(roles)))
        .optional()
        // The filter resolved it a moment ago: if it is gone, there is no LIV session (001-14).
        .orElseThrow(() ->
            new AuthenticationRejectedException(
                ErrorCodes.NOT_AUTHENTICATED, "User no longer registered"));
  }
}
