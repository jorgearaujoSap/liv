package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/**
 * The access token of a session, renewed with the refresh token (plan, «Sesión y tiempos»). It is
 * renewed when less than a minute is left, at the moment a request of the person needs it, and
 * always when the person chooses to stay connected ({@code keepalive}): that also restarts the
 * inactivity of the identity provider. The tokens live in the session, encrypted ({@link
 * EncryptedAuthorizedClientRepository}); the renewal is Spring Security's own.
 *
 * <p>Nothing of the request of the browser reaches the provider: Spring Security would otherwise
 * read a {@code scope} parameter of the request and send it with the refresh grant, so the browser
 * could shrink or change the scopes of the token that the session keeps.
 *
 * <p>With rotation of the refresh token off (realm, plan), two renewals at once do not close the
 * session: both succeed, and the last one to write wins.
 */
public final class TokenRenewal {

  private static final Logger LOG = LoggerFactory.getLogger(TokenRenewal.class);

  /**
   * The provider refused the renewal for good: the user was disabled or the session closed there.
   */
  private static final Set<String> REFUSED_FOR_GOOD = Set.of("invalid_grant", "invalid_token");

  /** A token with less than this left is renewed before it is forwarded (plan). */
  static final Duration RENEW_WHEN_LESS_THAN = Duration.ofSeconds(60);

  private final DefaultOAuth2AuthorizedClientManager whenNeeded;
  private final DefaultOAuth2AuthorizedClientManager always;
  private final OAuth2AuthorizedClientRepository clients;

  public TokenRenewal(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository clients,
      OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshClient) {
    this.clients = clients;
    RefreshTokenOAuth2AuthorizedClientProvider needed =
        new RefreshTokenOAuth2AuthorizedClientProvider();
    needed.setAccessTokenResponseClient(refreshClient);
    needed.setClockSkew(RENEW_WHEN_LESS_THAN);
    // A token is "about to expire" within this margin; with an hour, every token (they last five
    // minutes) is, so the renewal always happens.
    RefreshTokenOAuth2AuthorizedClientProvider forced =
        new RefreshTokenOAuth2AuthorizedClientProvider();
    forced.setAccessTokenResponseClient(refreshClient);
    forced.setClockSkew(Duration.ofHours(1));
    this.whenNeeded = manager(registrations, clients, needed);
    this.always = manager(registrations, clients, forced);
  }

  private static DefaultOAuth2AuthorizedClientManager manager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository clients,
      OAuth2AuthorizedClientProvider provider) {
    DefaultOAuth2AuthorizedClientManager manager =
        new DefaultOAuth2AuthorizedClientManager(registrations, clients);
    manager.setAuthorizedClientProvider(provider);
    // The default reads the "scope" parameter of the request of the browser.
    manager.setContextAttributesMapper(request -> Map.of());
    return manager;
  }

  /**
   * The access token to forward to the API, renewed first if less than a minute is left. If the
   * provider does not answer but the token still has life, the request goes on with it.
   *
   * @throws TokenRenewalException if the session has no usable token
   */
  public String accessToken(
      HttpServletRequest request, HttpServletResponse response, SessionAuthentication person) {
    try {
      OAuth2AuthorizedClient client = authorize(whenNeeded, request, response, person);
      if (!isAlive(client)) {
        // No refresh token, so it could not be renewed, and the token it has is over.
        throw new TokenRenewalException("El token de la sesión venció y no se pudo renovar", true);
      }
      return client.getAccessToken().getTokenValue();
    } catch (TokenRenewalException e) {
      if (e.sessionIsOver()) {
        throw e;
      }
      OAuth2AuthorizedClient current = stillWorking(request, person);
      if (current != null) {
        LOG.warn("El proveedor no renovó el token; la petición sigue con el que aún vale");
        return current.getAccessToken().getTokenValue();
      }
      throw e;
    }
  }

  /**
   * Renews the access token now, whatever time it has left.
   *
   * @throws TokenRenewalException if it cannot be renewed
   */
  public void renewNow(
      HttpServletRequest request, HttpServletResponse response, SessionAuthentication person) {
    OAuth2AuthorizedClient client = authorize(always, request, response, person);
    if (client.getRefreshToken() == null) {
      // Spring leaves the client as it was when there is nothing to renew with.
      throw new TokenRenewalException("La sesión no tiene refresh token", true);
    }
  }

  /**
   * The tokens the session already has, if they still work. Reading them can fail for the same
   * reason the renewal did (the provider does not answer): then there is no fallback, and the
   * failure stays what it was.
   */
  private OAuth2AuthorizedClient stillWorking(
      HttpServletRequest request, SessionAuthentication person) {
    try {
      OAuth2AuthorizedClient current =
          clients.loadAuthorizedClient(OidcRegistrations.USER, person, request);
      return current != null && isAlive(current) ? current : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isAlive(OAuth2AuthorizedClient client) {
    Instant expiresAt = client.getAccessToken().getExpiresAt();
    return expiresAt == null || expiresAt.isAfter(Instant.now());
  }

  private OAuth2AuthorizedClient authorize(
      DefaultOAuth2AuthorizedClientManager manager,
      HttpServletRequest request,
      HttpServletResponse response,
      SessionAuthentication person) {
    OAuth2AuthorizedClient client;
    try {
      client =
          manager.authorize(
              OAuth2AuthorizeRequest.withClientRegistrationId(OidcRegistrations.USER)
                  .principal(person)
                  .attribute(HttpServletRequest.class.getName(), request)
                  .attribute(HttpServletResponse.class.getName(), response)
                  .build());
    } catch (OAuth2AuthorizationException e) {
      String code = e.getError().getErrorCode();
      LOG.warn("No se pudo renovar el token de la sesión: {}", code);
      throw new TokenRenewalException(
          "No se pudo renovar el token", REFUSED_FOR_GOOD.contains(code));
    } catch (RuntimeException e) {
      LOG.warn("No se pudo renovar el token de la sesión: {}", e.getClass().getSimpleName());
      throw new TokenRenewalException("No se pudo renovar el token", false);
    }
    if (client == null) {
      throw new TokenRenewalException("La sesión no tiene tokens", true);
    }
    return client;
  }
}
