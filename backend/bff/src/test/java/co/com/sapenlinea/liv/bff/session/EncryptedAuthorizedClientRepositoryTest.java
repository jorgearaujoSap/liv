package co.com.sapenlinea.liv.bff.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

/**
 * The tokens of a session are kept encrypted in an attribute of the session and read back as the
 * rest of Spring Security expects them (001-11). A value that cannot be read, or that belongs to
 * another person, is never returned.
 */
@Tag("001-11")
class EncryptedAuthorizedClientRepositoryTest {

  private static final ClientRegistration REGISTRATION =
      ClientRegistration.withRegistrationId(OidcRegistrations.USER)
          .clientId("liv-bff")
          .clientSecret("secreto")
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("http://localhost/login/oauth2/code/liv")
          .authorizationUri("http://localhost/authorize")
          .tokenUri("http://localhost/token")
          .build();

  private final TokenCipher cipher =
      new TokenCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
  private final EncryptedAuthorizedClientRepository repository =
      new EncryptedAuthorizedClientRepository(
          cipher, id -> OidcRegistrations.USER.equals(id) ? REGISTRATION : null);
  private final Authentication ana = new TestingAuthenticationToken("usuario-1", "x");
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  private OAuth2AuthorizedClient client(OAuth2RefreshToken refresh) {
    Instant issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    return new OAuth2AuthorizedClient(
        REGISTRATION,
        "usuario-1",
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "acceso-secreto",
            issued,
            issued.plusSeconds(300),
            Set.of("openid")),
        refresh);
  }

  @Test
  @DisplayName("001-11 · Los tokens guardados se leen de vuelta tal cual")
  void roundTrip() {
    Instant issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    request.setSession(new MockHttpSession());
    repository.saveAuthorizedClient(
        client(new OAuth2RefreshToken("refresco-secreto", issued)), ana, request, response);

    OAuth2AuthorizedClient loaded =
        repository.loadAuthorizedClient(OidcRegistrations.USER, ana, request);

    assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("acceso-secreto");
    assertThat(loaded.getAccessToken().getExpiresAt()).isEqualTo(issued.plusSeconds(300));
    assertThat(loaded.getAccessToken().getScopes()).containsExactly("openid");
    assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresco-secreto");
    assertThat(loaded.getPrincipalName()).isEqualTo("usuario-1");
  }

  @Test
  @DisplayName("001-11 · Lo guardado en la sesión está cifrado")
  void theAttributeIsEncrypted() {
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    repository.saveAuthorizedClient(
        client(new OAuth2RefreshToken("refresco-secreto", Instant.now())), ana, request, response);

    byte[] stored = (byte[]) session.getAttribute(EncryptedAuthorizedClientRepository.ATTRIBUTE);

    assertThat(new String(stored, StandardCharsets.ISO_8859_1))
        .doesNotContain("acceso-secreto")
        .doesNotContain("refresco-secreto");
  }

  @Test
  @DisplayName("001-11 · Sin refresh token se guarda y se lee sin él")
  void withoutRefreshToken() {
    request.setSession(new MockHttpSession());
    repository.saveAuthorizedClient(client(null), ana, request, response);

    OAuth2AuthorizedClient loaded =
        repository.loadAuthorizedClient(OidcRegistrations.USER, ana, request);

    assertThat(loaded.getRefreshToken()).isNull();
  }

  @Test
  @DisplayName("001-11 · Sin sesión, o con otro cliente OIDC, no hay tokens")
  void nothingWithoutASessionOrForAnotherClient() {
    assertThat(
            (OAuth2AuthorizedClient)
                repository.loadAuthorizedClient(OidcRegistrations.USER, ana, request))
        .isNull();

    request.setSession(new MockHttpSession());
    repository.saveAuthorizedClient(client(null), ana, request, response);
    assertThat(
            (OAuth2AuthorizedClient)
                repository.loadAuthorizedClient(OidcRegistrations.SERVICE, ana, request))
        .isNull();
  }

  @Test
  @DisplayName("001-11 · Un valor ilegible, o de otra persona, no se devuelve")
  void anUnreadableOrForeignValueIsNotReturned() {
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    repository.saveAuthorizedClient(client(null), ana, request, response);

    Authentication other = new TestingAuthenticationToken("usuario-2", "x");
    assertThat(
            (OAuth2AuthorizedClient)
                repository.loadAuthorizedClient(OidcRegistrations.USER, other, request))
        .as("tokens de otra persona")
        .isNull();

    session.setAttribute(EncryptedAuthorizedClientRepository.ATTRIBUTE, new byte[] {1, 2, 3, 4});
    assertThat(
            (OAuth2AuthorizedClient)
                repository.loadAuthorizedClient(OidcRegistrations.USER, ana, request))
        .as("valor ilegible")
        .isNull();
  }

  @Test
  @DisplayName("001-11 · Quitar el cliente borra los tokens de la sesión")
  void removeDeletesTheTokens() {
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    repository.saveAuthorizedClient(client(null), ana, request, response);

    repository.removeAuthorizedClient(OidcRegistrations.USER, ana, request, response);

    assertThat(session.getAttribute(EncryptedAuthorizedClientRepository.ATTRIBUTE)).isNull();
  }
}
