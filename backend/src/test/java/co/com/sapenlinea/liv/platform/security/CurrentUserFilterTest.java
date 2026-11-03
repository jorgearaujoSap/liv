package co.com.sapenlinea.liv.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("Filtro del usuario actual (T12)")
class CurrentUserFilterTest {

  private static final UUID ADMINISTRATION = UUID.randomUUID();

  private final CurrentUserFilter filter =
      new CurrentUserFilter(
          (issuer, subject) ->
              Optional.of(new DirectoryUser(UUID.randomUUID(), ADMINISTRATION, true)),
          new ClaimsAdapter(new LivSecurityProperties.Claims("administration_id", "roles")));

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · El contexto de administración se cierra aunque el controlador falle")
  void closesTheContextOnFailure() {
    authenticate();
    AtomicReference<Optional<UUID>> seen = new AtomicReference<>();

    assertThatThrownBy(() ->
            filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> {
                  seen.set(AdministrationContext.current());
                  throw new IllegalStateException("falla del controlador");
                }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(seen.get()).contains(ADMINISTRATION);
    assertThat(AdministrationContext.current()).isEmpty();
  }

  private static void authenticate() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuer("https://idp.test/realms/liv")
            .subject("subject-1")
            .claim("administration_id", ADMINISTRATION.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
