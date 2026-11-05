package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
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
 * <p>With rotation of the refresh token off (realm, plan), two renewals at once do not close the
 * session: both succeed.
 */
public final class TokenRenewal {

  private static final Logger LOG = LoggerFactory.getLogger(TokenRenewal.class);
  private static final String GRANT_REFUSED = "invalid_grant";

  private final DefaultOAuth2AuthorizedClientManager whenNeeded;
  private final DefaultOAuth2AuthorizedClientManager always;

  public TokenRenewal(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository clients,
      OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshClient) {
    RefreshTokenOAuth2AuthorizedClientProvider needed =
        new RefreshTokenOAuth2AuthorizedClientProvider();
    needed.setAccessTokenResponseClient(refreshClient);
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
    return manager;
  }

  /**
   * The access token to forward to the API, renewed first if less than a minute is left.
   *
   * @throws TokenRenewalException if it cannot be renewed
   */
  public String accessToken(
      HttpServletRequest request, HttpServletResponse response, SessionAuthentication person) {
    return authorize(whenNeeded, request, response, person).getAccessToken().getTokenValue();
  }

  /**
   * Renews the access token now, whatever time it has left.
   *
   * @throws TokenRenewalException if it cannot be renewed
   */
  public void renewNow(
      HttpServletRequest request, HttpServletResponse response, SessionAuthentication person) {
    authorize(always, request, response, person);
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
      throw new TokenRenewalException("No se pudo renovar el token", GRANT_REFUSED.equals(code));
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
