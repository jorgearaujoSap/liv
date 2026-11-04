package co.com.sapenlinea.liv.platform.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.EventsPage;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.Kind;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.ProviderEvent;
import co.com.sapenlinea.liv.platform.RealKeycloak;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The adapter of the events of the identity provider (plan step 9) against the real Keycloak of
 * LIV, with the realm as code and the client {@code liv-audit-importer} by client credentials: the
 * real shape of the events (ids, types, errors), the filter by instant, the order, the pages and
 * the lockout of the realm. Nothing of the adapter is replaced; pages of two events make the tests
 * cross pages.
 */
@DisplayName("Eventos de Keycloak real para la auditoría (T15)")
class KeycloakIdentityProviderEventsTest {

  private static final int PAGE = 2;

  private final IdentityProviderEvents events =
      KeycloakIdentityProviderEvents.forProvider(
          RealKeycloak.issuer(),
          null,
          "liv-audit-importer",
          RealKeycloak.AUDIT_IMPORTER_SECRET,
          true,
          PAGE);

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Lee de Keycloak los inicios fallidos, de usuarios conocidos y desconocidos")
  void readsFailedLogins() {
    Instant from = Instant.now().minus(Duration.ofSeconds(5));
    String unknown = "no-existe-" + UUID.randomUUID();
    RealKeycloak.failedLogin(unknown);
    RealKeycloak.failedLogin(RealKeycloak.USER);

    EventsPage read = events.userEvents(from, 100);

    assertThat(read.more()).isFalse();
    assertThat(read.events())
        .allSatisfy(event -> assertThat(event.kind()).isEqualTo(Kind.USER))
        .allSatisfy(event -> assertThat(event.time()).isAfterOrEqualTo(from));
    assertThat(read.events())
        .filteredOn(event -> unknown.equals(event.attemptedUsername()))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.id()).isNotBlank();
          assertThat(event.type()).isEqualTo("LOGIN_ERROR");
          assertThat(event.error()).isEqualTo("user_not_found");
          assertThat(event.subject()).isNull();
          assertThat(event.ipAddress()).isNotBlank();
        });
    assertThat(read.events())
        .filteredOn(event -> RealKeycloak.USER.equals(event.attemptedUsername()))
        .last()
        .satisfies(event -> {
          assertThat(event.error()).isEqualTo("invalid_user_credentials");
          assertThat(event.subject()).isNotBlank();
        });
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Lee por páginas desde un instante, los más antiguos primero, y avisa si quedan más")
  void readsPagesOldestFirstAndTellsWhenThereIsMore() {
    Instant from = Instant.now().minus(Duration.ofMillis(500));
    List<String> usernames =
        IntStream.range(0, 5).mapToObj(i -> "pagina-" + i + "-" + UUID.randomUUID()).toList();
    usernames.forEach(RealKeycloak::failedLogin);

    EventsPage first = events.userEvents(from, 3);
    EventsPage all = events.userEvents(from, 100);

    assertThat(first.more()).isTrue();
    assertThat(first.events()).hasSize(3);
    assertThat(all.more()).isFalse();
    assertThat(all.events()).isSortedAccordingTo(Comparator.comparing(ProviderEvent::time));
    List<String> ours =
        all.events().stream()
            .map(ProviderEvent::attemptedUsername)
            .filter(usernames::contains)
            .toList();
    // Five events in pages of two, oldest first: the order in which they happened.
    assertThat(ours).containsExactlyElementsOf(usernames);
    assertThat(all.events().subList(0, 3)).isEqualTo(first.events());
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Lee de Keycloak el bloqueo de un usuario tras los intentos fallidos")
  void readsALockout() {
    Instant from = Instant.now().minus(Duration.ofSeconds(5));
    String username = "bloqueado-" + UUID.randomUUID();
    String userId = RealKeycloak.createUser(username);
    // failureFactor 5 of the realm.
    IntStream.range(0, 6).forEach(i -> RealKeycloak.failedLogin(username));

    EventsPage read = events.userEvents(from, 100);

    assertThat(read.events())
        .filteredOn(event -> userId.equals(event.subject()))
        .extracting(ProviderEvent::type)
        .contains("USER_DISABLED_BY_TEMPORARY_LOCKOUT");
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Lee de Keycloak el alta de un usuario y el rol que le da un administrador")
  void readsAdministratorChanges() {
    Instant from = Instant.now().minus(Duration.ofSeconds(5));
    String userId = RealKeycloak.createUser("auditoria-" + UUID.randomUUID());
    RealKeycloak.grantRealmRole(userId, "AUDITOR");

    EventsPage read = events.adminEvents(from, 100);

    List<ProviderEvent> ofUser =
        read.events().stream().filter(event -> userId.equals(event.subject())).toList();
    assertThat(ofUser)
        .extracting(ProviderEvent::type)
        .containsExactly("USER_CREATE", "REALM_ROLE_MAPPING_CREATE");
    ProviderEvent grant = ofUser.getLast();
    assertThat(grant.kind()).isEqualTo(Kind.ADMIN);
    assertThat(grant.id()).isNotBlank();
    assertThat(grant.authorSubject()).isNotBlank();
    assertThat(grant.attemptedUsername()).isNull();
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Solo devuelve eventos desde el instante pedido")
  void filtersByInstant() {
    RealKeycloak.failedLogin("anterior-" + UUID.randomUUID());
    Instant after = Instant.now().plus(Duration.ofSeconds(1));

    assertThat(events.userEvents(after, 100).events()).isEmpty();
    assertThat(events.adminEvents(after, 100).events()).isEmpty();
  }
}
