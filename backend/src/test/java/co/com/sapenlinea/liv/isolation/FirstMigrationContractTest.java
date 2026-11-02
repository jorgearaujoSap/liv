package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.asSuperuser;
import static co.com.sapenlinea.liv.isolation.LivDatabase.createEmptyDatabase;
import static co.com.sapenlinea.liv.isolation.LivDatabase.migrate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * La primera migración comprueba el contrato de roles y de la base antes de crear nada, para que un
 * entorno creado por la infraestructura (AWS) falle con un mensaje claro y no a medias.
 */
@Tag("001-17")
class FirstMigrationContractTest {

  @Test
  @DisplayName(
      "001-17 · Si liv_owner no es el dueño de la base, la migración se detiene: los REVOKE no surtirían efecto")
  void databaseOwnedBySomeoneElseStopsTheMigration() throws SQLException {
    String url = createEmptyDatabase("liv_contract_owner", "postgres");
    asSuperuser("GRANT CREATE ON DATABASE liv_contract_owner TO liv_owner");

    assertThatThrownBy(() -> migrate(url))
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("debe ser el dueño de la base de datos");
  }

  @Test
  @DisplayName(
      "001-17 · Si liv_owner no puede asumir los dueños de las funciones, la migración se detiene")
  void missingMembershipStopsTheMigration() throws SQLException {
    String url = createEmptyDatabase("liv_contract_membership");
    asSuperuser("REVOKE liv_identity_lookup FROM liv_owner");
    try {
      assertThatThrownBy(() -> migrate(url))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("debe poder asumir liv_identity_lookup y liv_audit_platform");
    } finally {
      asSuperuser("GRANT liv_identity_lookup TO liv_owner WITH INHERIT FALSE, SET TRUE");
    }
  }

  @Test
  @DisplayName(
      "001-17 · Si un dueño de función puede iniciar sesión o liv_app es miembro de otro rol, la migración se detiene")
  void overPrivilegedRolesStopTheMigration() throws SQLException {
    String url = createEmptyDatabase("liv_contract_roles");
    asSuperuser("ALTER ROLE liv_audit_platform LOGIN");
    try {
      assertThatThrownBy(() -> migrate(url))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("no pueden iniciar sesión");
    } finally {
      asSuperuser("ALTER ROLE liv_audit_platform NOLOGIN");
    }

    asSuperuser("GRANT liv_identity_lookup TO liv_app");
    try {
      assertThatThrownBy(() -> migrate(url))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("liv_app no puede ser miembro");
    } finally {
      asSuperuser("REVOKE liv_identity_lookup FROM liv_app");
    }
  }
}
