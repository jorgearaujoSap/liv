package co.com.sapenlinea.liv.bff.session;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.FakeBackend;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

/**
 * Revoking the refresh token (RFC 7009, 001-09): the endpoint is the one the provider publishes in
 * its discovery document, unless the configuration names another; it never throws, whatever goes
 * wrong, because a session always ends.
 */
@Tag("001-09")
class RefreshTokenRevokerTest {

  private final FakeBackend backend = new FakeBackend();

  @AfterEach
  void reset() {
    backend.reset();
  }

  private OAuth2AuthorizedClient client(Map<String, Object> metadata, OAuth2RefreshToken refresh) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("liv")
            .clientId("liv-bff")
            .clientSecret("secreto")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/liv")
            .authorizationUri("http://localhost/authorize")
            .tokenUri("http://localhost/token")
            .providerConfigurationMetadata(metadata)
            .build();
    return new OAuth2AuthorizedClient(
        registration,
        "usuario-1",
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "acceso",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Set.of()),
        refresh);
  }

  private RefreshTokenRevoker revoker(String configuredEndpoint) {
    return new RefreshTokenRevoker(
        Http.client(Duration.ofSeconds(2), Duration.ofSeconds(5)), configuredEndpoint);
  }

  @Test
  @DisplayName("001-09 · Usa el revocation_endpoint que publica el proveedor")
  void usesThePublishedEndpoint() {
    OAuth2AuthorizedClient client =
        client(
            Map.of("revocation_endpoint", backend.revocationUri()),
            new OAuth2RefreshToken("refresco", Instant.now()));

    revoker(null).revoke(client);

    assertThat(backend.revocationCalls()).hasSize(1);
    assertThat(backend.revocationCalls().get(0).body()).contains("token=refresco");
  }

  @Test
  @DisplayName("001-09 · El endpoint configurado tiene prioridad sobre el publicado")
  void theConfiguredEndpointWins() {
    OAuth2AuthorizedClient client =
        client(
            Map.of("revocation_endpoint", "http://localhost:1/otro"),
            new OAuth2RefreshToken("refresco", Instant.now()));

    revoker(backend.revocationUri()).revoke(client);

    assertThat(backend.revocationCalls()).hasSize(1);
  }

  @Test
  @DisplayName(
      "001-09 · Sin endpoint, sin refresh token o con un fallo, no lanza ni llama a nadie de más")
  void neverThrows() {
    revoker(null).revoke(client(Map.of(), new OAuth2RefreshToken("refresco", Instant.now())));
    revoker(backend.revocationUri()).revoke(client(Map.of(), null));
    revoker("no es una dirección")
        .revoke(client(Map.of(), new OAuth2RefreshToken("r", Instant.now())));
    revoker("http://localhost:1/cerrado")
        .revoke(client(Map.of(), new OAuth2RefreshToken("refresco", Instant.now())));
    backend.revocationAnswers(500);
    revoker(backend.revocationUri())
        .revoke(client(Map.of(), new OAuth2RefreshToken("refresco", Instant.now())));

    assertThat(backend.revocationCalls()).as("solo la última llega al proveedor").hasSize(1);
  }
}
