package co.com.sapenlinea.liv.bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/**
 * The renewal of the access token and what it decides when it fails (plan, «Sesión y tiempos»,
 * 001-10, 001-22): the session is over when the provider refuses for good or there is nothing to
 * renew with; it stays, and the request goes on with the token it still has, when the provider only
 * failed for a moment. A token that is over is never forwarded.
 */
@Tag("001-10")
class TokenRenewalTest {

  private static final ClientRegistration REGISTRATION =
      ClientRegistration.withRegistrationId(OidcRegistrations.USER)
          .clientId("liv-bff")
          .clientSecret("secreto")
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("http://localhost/login/oauth2/code/liv")
          .authorizationUri("http://localhost/authorize")
          .tokenUri("http://localhost/token")
          .build();

  private static final SessionAuthentication PERSON =
      new SessionAuthentication(new SessionIdentity("http://emisor", "usuario-1", "sid-1"));

  /** The tokens of the session, as the encrypted repository would hold them. */
  private static final class Tokens implements OAuth2AuthorizedClientRepository {
    OAuth2AuthorizedClient client;
    boolean failsToRead;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
        String id, Authentication principal, HttpServletRequest request) {
      if (failsToRead) {
        throw new IllegalStateException("el proveedor no responde (descubrimiento)");
      }
      return (T) client;
    }

    @Override
    public void saveAuthorizedClient(
        OAuth2AuthorizedClient saved,
        Authentication principal,
        HttpServletRequest request,
        HttpServletResponse response) {
      client = saved;
    }

    @Override
    public void removeAuthorizedClient(
        String id,
        Authentication principal,
        HttpServletRequest request,
        HttpServletResponse response) {
      client = null;
    }
  }

  private final Tokens tokens = new Tokens();
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  private static OAuth2AuthorizedClient clientWith(Duration lifeLeft, boolean withRefreshToken) {
    Instant now = Instant.now();
    return new OAuth2AuthorizedClient(
        REGISTRATION,
        "usuario-1",
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "acceso-actual",
            now.minus(Duration.ofMinutes(5)),
            now.plus(lifeLeft),
            Set.of()),
        withRefreshToken ? new OAuth2RefreshToken("refresco", now.minusSeconds(300)) : null);
  }

  private TokenRenewal renewalWhereProviderAnswers(
      OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> provider) {
    return new TokenRenewal(id -> REGISTRATION, tokens, provider);
  }

  private static OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refusing(
      String errorCode) {
    return grant -> {
      throw new OAuth2AuthorizationException(new OAuth2Error(errorCode));
    };
  }

  @Test
  @DisplayName(
      "001-10 · invalid_grant e invalid_token terminan la sesión: el proveedor ya no la acepta")
  void aRefusalForGoodEndsTheSession() {
    for (String code : new String[] {"invalid_grant", "invalid_token"}) {
      tokens.client = clientWith(Duration.ofSeconds(10), true);
      TokenRenewal renewal = renewalWhereProviderAnswers(refusing(code));

      assertThatThrownBy(() -> renewal.accessToken(request, response, PERSON))
          .as(code)
          .isInstanceOfSatisfying(
              TokenRenewalException.class, e -> assertThat(e.sessionIsOver()).isTrue());
    }
  }

  @Test
  @DisplayName(
      "001-10 · Si el proveedor falla un momento y al token aún le queda vida, se sigue con ese token")
  void aPassingFailureKeepsTheTokenThatStillWorks() {
    tokens.client = clientWith(Duration.ofSeconds(30), true);

    String token =
        renewalWhereProviderAnswers(refusing("server_error"))
            .accessToken(request, response, PERSON);

    assertThat(token).isEqualTo("acceso-actual");
  }

  @Test
  @DisplayName(
      "001-10 · Si el proveedor falla un momento y el token ya venció, no se reenvía: la sesión sigue y la persona reintenta")
  void aPassingFailureWithAnExpiredTokenIsNotForwarded() {
    tokens.client = clientWith(Duration.ofSeconds(-30), true);

    assertThatThrownBy(() ->
            renewalWhereProviderAnswers(refusing("server_error"))
                .accessToken(request, response, PERSON))
        .isInstanceOfSatisfying(
            TokenRenewalException.class, e -> assertThat(e.sessionIsOver()).isFalse());
  }

  @Test
  @DisplayName(
      "001-10 · Si además el repositorio de tokens falla (el proveedor no responde), sigue siendo un fallo pasajero de la renovación")
  void aFailureReadingTheFallbackIsStillAPassingFailure() {
    tokens.client = clientWith(Duration.ofSeconds(-30), true);
    TokenRenewal renewal = renewalWhereProviderAnswers(refusing("server_error"));
    tokens.failsToRead = true;

    assertThatThrownBy(() -> renewal.accessToken(request, response, PERSON))
        .isInstanceOfSatisfying(
            TokenRenewalException.class, e -> assertThat(e.sessionIsOver()).isFalse());
  }

  @Test
  @DisplayName(
      "001-10 · Sin refresh token y con el token vencido, la sesión termina: no hay con qué renovar")
  void withoutARefreshTokenAnExpiredTokenEndsTheSession() {
    tokens.client = clientWith(Duration.ofSeconds(-30), false);

    assertThatThrownBy(() ->
            renewalWhereProviderAnswers(refusing("server_error"))
                .accessToken(request, response, PERSON))
        .isInstanceOfSatisfying(
            TokenRenewalException.class, e -> assertThat(e.sessionIsOver()).isTrue());
  }

  @Test
  @DisplayName(
      "001-22 · keepalive sin refresh token termina la sesión en vez de responder que renovó")
  void keepAliveWithoutARefreshTokenEndsTheSession() {
    tokens.client = clientWith(Duration.ofMinutes(4), false);

    assertThatThrownBy(() ->
            renewalWhereProviderAnswers(refusing("server_error"))
                .renewNow(request, response, PERSON))
        .isInstanceOfSatisfying(
            TokenRenewalException.class, e -> assertThat(e.sessionIsOver()).isTrue());
  }

  @Test
  @DisplayName(
      "001-22 · Un token con más de un minuto de vida no se renueva al reenviar; con menos, sí")
  void theMarginIsOneMinute() {
    OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> renews =
        grant ->
            OAuth2AccessTokenResponse.withToken("acceso-renovado")
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(300)
                .build();
    TokenRenewal renewal = renewalWhereProviderAnswers(renews);

    tokens.client = clientWith(Duration.ofSeconds(90), true);
    assertThat(renewal.accessToken(request, response, PERSON)).isEqualTo("acceso-actual");

    tokens.client = clientWith(Duration.ofSeconds(45), true);
    assertThat(renewal.accessToken(request, response, PERSON)).isEqualTo("acceso-renovado");
  }
}
