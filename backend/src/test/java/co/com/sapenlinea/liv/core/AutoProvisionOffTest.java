package co.com.sapenlinea.liv.core;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Decision of the project lead (plan › Decisiones, T13): automatic registration can be turned off,
 * for example while the identity provider changes, so that unknown (issuer, subject) pairs are
 * relinked explicitly instead of becoming new users.
 */
@TestPropertySource(properties = "liv.sessions.auto-provision=false")
@Import(SessionEventsRecorder.class)
@DisplayName("Alta automática apagada (T13)")
class AutoProvisionOffTest extends PlatformWebTest {

  @Autowired SessionEventsRecorder.Recorded events;

  @Test
  @Tag("001-17")
  @DisplayName(
      "001-17 · Con el alta automática apagada, un usuario desconocido no entra ni se crea")
  void unknownUserIsNotRegistered() {
    String subject = "desconocido-" + UUID.randomUUID();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("X-User-Token", token);

    HttpResponse<String> response =
        post(
            "/internal/v1/sessions",
            headers,
            "{\"sid\":\"" + OidcTestProvider.sessionIdOf(token) + "\"}");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("\"code\":\"001-17\"");
    String sid = OidcTestProvider.sessionIdOf(token);
    assertThat(events.of(SessionDenied.class))
        .filteredOn(e -> sid.equals(e.sid()))
        .containsExactly(
            new SessionDenied("001-17", sid, null, null, LivDatabase.ADMIN_A, null, null, null));
    assertThat(
            LivDatabase.readAsOwner(
                "SELECT id FROM core.admin_user WHERE identity_subject = ?", subject))
        .isEmpty();
  }

  @Test
  @DisplayName("001-21 · Con el alta automática apagada, un usuario registrado sigue entrando")
  void registeredUserStillStarts() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("X-User-Token", token);

    assertThat(
            post(
                    "/internal/v1/sessions",
                    headers,
                    "{\"sid\":\"" + OidcTestProvider.sessionIdOf(token) + "\"}")
                .statusCode())
        .isEqualTo(201);
  }
}
