package co.com.sapenlinea.liv.platform.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.Kind;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.ProviderEvent;
import co.com.sapenlinea.liv.platform.RealKeycloak;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The adapter of the events of the identity provider (plan step 9) against the real Keycloak of
 * LIV, with the realm as code and the client {@code liv-audit-importer} by client credentials: the
 * real shape of the events (ids, types, errors, the representation of a role mapping) and the
 * filter by date. Nothing of the adapter is replaced.
 */
@DisplayName("Eventos de Keycloak real para la auditoría (T15)")
class KeycloakIdentityProviderEventsTest {

  private final IdentityProviderEvents events =
      KeycloakIdentityProviderEvents.forProvider(
          RealKeycloak.issuer(), "liv-audit-importer", RealKeycloak.AUDIT_IMPORTER_SECRET, true);

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Lee de Keycloak los inicios fallidos, de usuarios conocidos y desconocidos")
  void readsFailedLogins() {
    Instant from = Instant.now().minus(Duration.ofMinutes(1));
    String unknown = "no-existe-" + UUID.randomUUID();
    RealKeycloak.failedLogin(unknown);
    RealKeycloak.failedLogin(RealKeycloak.USER);

    List<ProviderEvent> read = events.userEvents(from, 100);

    assertThat(read).isSortedAccordingTo(Comparator.comparing(ProviderEvent::time));
    assertThat(read).allSatisfy(event -> assertThat(event.kind()).isEqualTo(Kind.USER));
    assertThat(read)
        .filteredOn(event -> unknown.equals(event.attemptedUsername()))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.id()).isNotBlank();
          assertThat(event.type()).isEqualTo("LOGIN_ERROR");
          assertThat(event.error()).isEqualTo("user_not_found");
          assertThat(event.subject()).isNull();
          assertThat(event.ipAddress()).isNotBlank();
          assertThat(event.time()).isAfterOrEqualTo(from);
        });
    assertThat(read)
        .filteredOn(event -> RealKeycloak.USER.equals(event.attemptedUsername()))
        .last()
        .satisfies(event -> {
          assertThat(event.type()).isEqualTo("LOGIN_ERROR");
          assertThat(event.error()).isEqualTo("invalid_user_credentials");
          assertThat(event.subject()).isNotBlank();
        });
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Lee de Keycloak el alta de un usuario y el rol que le da un administrador")
  void readsAdministratorChanges() {
    Instant from = Instant.now().minus(Duration.ofMinutes(1));
    String userId = RealKeycloak.createUser("auditoria-" + UUID.randomUUID());
    RealKeycloak.grantRealmRole(userId, "AUDITOR");

    List<ProviderEvent> read = events.adminEvents(from, 100);

    assertThat(read).isSortedAccordingTo(Comparator.comparing(ProviderEvent::time));
    List<ProviderEvent> ofUser =
        read.stream().filter(event -> userId.equals(event.subject())).toList();
    assertThat(ofUser)
        .extracting(ProviderEvent::type)
        .containsExactly("USER_CREATE", "REALM_ROLE_MAPPING_CREATE");
    assertThat(ofUser.getFirst().enabled()).isTrue();
    ProviderEvent grant = ofUser.getLast();
    assertThat(grant.kind()).isEqualTo(Kind.ADMIN);
    assertThat(grant.id()).isNotBlank();
    assertThat(grant.roles()).containsExactly("AUDITOR");
    assertThat(grant.authorSubject()).isNotBlank();
    assertThat(grant.attemptedUsername()).isNull();
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Solo devuelve eventos desde la fecha pedida")
  void filtersByDate() {
    RealKeycloak.failedLogin("posterior-" + UUID.randomUUID());

    assertThat(events.userEvents(Instant.now().plus(Duration.ofMinutes(1)), 100)).isEmpty();
    assertThat(events.adminEvents(Instant.now().plus(Duration.ofMinutes(1)), 100)).isEmpty();
  }
}
