package co.com.sapenlinea.liv.bff.session;

import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * The client credentials token of {@code liv-bff-service}, which the internal endpoints of the API
 * require (ADR 0025). It is kept in memory and renewed when it expires: it belongs to the BFF, not
 * to a person, so it never goes to a session.
 */
public final class ServiceTokens {

  private final OAuth2AuthorizedClientManager manager;

  public ServiceTokens(ClientRegistrationRepository registrations) {
    AuthorizedClientServiceOAuth2AuthorizedClientManager clients =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
    clients.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
    this.manager = clients;
  }

  /** The current access token. It fails if the provider does not issue one. */
  public String accessToken() {
    OAuth2AuthorizedClient client =
        manager.authorize(
            OAuth2AuthorizeRequest.withClientRegistrationId(OidcRegistrations.SERVICE)
                .principal(OidcRegistrations.SERVICE)
                .build());
    if (client == null) {
      throw new IllegalStateException("No se obtuvo el token de servicio del BFF");
    }
    return client.getAccessToken().getTokenValue();
  }
}
