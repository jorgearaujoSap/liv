package co.com.sapenlinea.liv.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

@DisplayName("Lectura de los claims del token (T12)")
class ClaimsAdapterTest {

  private static final UUID ADMINISTRATION = UUID.randomUUID();

  /** Another provider may name the claims differently (D-001-6, 001-20). */
  private final ClaimsAdapter adapter =
      new ClaimsAdapter(new LivSecurityProperties.Claims("tenant", "groups"));

  @Test
  @DisplayName("001-20 · Los nombres de los claims se configuran")
  void configurableNames() {
    TokenIdentity identity =
        adapter.identityOf(
            jwt()
                .claim("tenant", ADMINISTRATION.toString())
                .claim("groups", List.of("CONSULTA", "AUDITOR"))
                .build());

    assertThat(identity.issuer()).isEqualTo("https://idp.test/realms/liv");
    assertThat(identity.subject()).isEqualTo("subject-1");
    assertThat(identity.administrationId()).contains(ADMINISTRATION);
    assertThat(identity.roles()).containsExactlyInAnyOrder("CONSULTA", "AUDITOR");
  }

  @Test
  @DisplayName("001-17 · Una administración ausente o que no es UUID queda vacía")
  void invalidAdministration() {
    assertThat(adapter.identityOf(jwt().build()).administrationId()).isEmpty();
    assertThat(adapter.identityOf(jwt().claim("tenant", "no-uuid").build()).administrationId())
        .isEmpty();
    assertThat(adapter.identityOf(jwt().build()).roles()).isEmpty();
  }

  private static Jwt.Builder jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuer("https://idp.test/realms/liv")
        .subject("subject-1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300));
  }
}
