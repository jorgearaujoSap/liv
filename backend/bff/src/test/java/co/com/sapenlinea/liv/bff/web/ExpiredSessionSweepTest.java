package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * A session that nobody comes back to also ends as it should (001-10, 001-18; T19): the sweep finds
 * it expired, tells LIV (EndSession with {@code IDLE}), revokes it in the provider and deletes it.
 * Here the inactivity is 3 seconds and the sweep runs every second; the context closes at the end,
 * so its sweep does not touch the sessions of other tests.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"spring.session.timeout=3s", "liv.bff.session.sweep-interval=1s"})
class ExpiredSessionSweepTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @Tag("001-18")
  @DisplayName(
      "001-10 · Una sesión inactiva termina aunque la persona no vuelva: EndSession con IDLE, revocación y borrado")
  void anIdleSessionEndsWithoutThePersonComingBack() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();

    pause(6000);

    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).path())
        .isEqualTo("/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end");
    assertThat(BACKEND.endSessionCalls().get(0).body())
        .contains("\"reason\":\"IDLE\"")
        .contains("\"subject\":\"" + OidcProvider.SUBJECT + "\"");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Un inicio de sesión que nadie terminó se borra al vencer, sin avisar a LIV")
  void anUnfinishedSignInIsDeleted() throws SQLException {
    assertThat(browser().get(bffBase() + "/bff/login").status()).isEqualTo(302);
    assertThat(storedSessions()).isEqualTo(1);

    pause(6000);

    assertThat(storedSessions()).isZero();
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @Tag("001-10")
  @Tag("001-18")
  @DisplayName(
      "001-10 · Si LIV no responde, la sesión vencida no se borra: el barrido siguiente lo vuelve a intentar")
  void aFailedEndIsTriedAgain() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    BACKEND.endSessionAnswers(503);

    pause(5000);
    int attempts = BACKEND.endSessionCalls().size();
    int keptWhileFailing = storedSessions();
    BACKEND.endSessionAnswers(204);
    pause(2500);

    assertThat(attempts).as("lo intentó y falló").isGreaterThan(0);
    assertThat(keptWhileFailing).as("mientras LIV falla, la sesión queda").isEqualTo(1);
    assertThat(storedSessions()).as("con LIV de vuelta, termina").isZero();
    assertThat(BACKEND.endSessionCalls()).hasSizeGreaterThan(attempts);
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Una sesión vencida que no se puede leer (otra versión de una clase) se borra igual, sin llamar a LIV")
  void anUnreadableExpiredSessionIsDeleted() throws SQLException {
    browser().signIn("/");
    BffDatabase.execute(
        BffDatabase.jdbcUrl(),
        BffDatabase.OWNER,
        "UPDATE bff.spring_session_attributes SET attribute_bytes = decode('00', 'hex')"
            + " WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'");
    BACKEND.reset();

    pause(6000);

    assertThat(storedSessions()).isZero();
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · El barrido toma su bloqueo en bff.shedlock: con varias réplicas, una sola barre a la vez")
  void theSweepTakesItsLock() throws SQLException {
    // The row is not deleted first: ShedLock remembers it exists and would take the lock as held.
    pause(2500);

    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT count(*) FROM bff.shedlock WHERE name = 'bff-expired-sessions'"
                    + " AND locked_at > timezone('utc', now()) - interval '3 seconds'"))
        .as("lo tomó este contexto hace menos de 3 segundos")
        .isEqualTo("1");
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Una sesión que otra réplica está terminando (fila bloqueada) se salta sin esperar: las demás siguen")
  void aSessionLockedByAnotherReplicaIsSkipped() throws SQLException {
    browser().signIn("/");
    pause(500);
    browser().signIn("/");
    BACKEND.reset();

    try (Connection other =
        DriverManager.getConnection(
            BffDatabase.jdbcUrl(), BffDatabase.APP, BffDatabase.password(BffDatabase.APP))) {
      other.setAutoCommit(false);
      try (Statement lock = other.createStatement()) {
        // La más antigua, que es la primera que el barrido intenta terminar.
        lock.execute(
            "SELECT 1 FROM bff.spring_session WHERE primary_id ="
                + " (SELECT primary_id FROM bff.spring_session ORDER BY creation_time LIMIT 1)"
                + " FOR UPDATE");
      }
      pause(5000);
      assertThat(BACKEND.endSessionCalls())
          .as("la otra sí terminó: el barrido no se quedó esperando la bloqueada")
          .hasSize(1);
      assertThat(storedSessions()).as("la bloqueada sigue").isEqualTo(1);
      other.rollback();
    }
    pause(2000);

    assertThat(BACKEND.endSessionCalls()).as("cuando la sueltan, termina una sola vez").hasSize(2);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Un 401 de LIV (credencial del servicio) se reintenta; un 400 se registra como error y no se reintenta")
  void onlyARefusalForGoodIsNotTriedAgain() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    BACKEND.endSessionAnswers(401);
    pause(5000);
    int keptAfter401 = storedSessions();

    BACKEND.endSessionAnswers(400);
    pause(2000);

    assertThat(keptAfter401).as("401: queda para reintentar").isEqualTo(1);
    assertThat(storedSessions()).as("400: no tiene arreglo, se borra").isZero();
  }
}
