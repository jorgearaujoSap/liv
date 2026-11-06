package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.BffConnectionCheck;
import co.com.sapenlinea.liv.bff.session.EncryptedAuthorizedClientRepository;
import co.com.sapenlinea.liv.bff.session.Http;
import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
import co.com.sapenlinea.liv.bff.session.ServiceTokens;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import co.com.sapenlinea.liv.bff.session.SessionLifetime;
import co.com.sapenlinea.liv.bff.session.TokenCipher;
import co.com.sapenlinea.liv.bff.session.TokenRenewal;
import java.time.Clock;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.client.RestClient;

/**
 * The pieces of the session and of the calls to the identity provider and to liv-api, built from
 * the configuration (ADR 0010). They are plain classes of the {@code session} package; this is the
 * only place that knows how they are assembled.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BffProperties.class)
class SessionConfiguration {

  /** {@code liv-bff} (code with PKCE) and {@code liv-bff-service} (client credentials). */
  @Bean
  ClientRegistrationRepository clientRegistrations(BffProperties properties) {
    BffProperties.Oidc oidc = properties.oidc();
    Supplier<ClientRegistration> user =
        () ->
            ClientRegistrations.fromIssuerLocation(oidc.issuerUri())
                .registrationId(OidcRegistrations.USER)
                .clientId(oidc.clientId())
                .clientSecret(oidc.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(oidc.redirectUri())
                .scope("openid")
                .build();
    Supplier<ClientRegistration> service =
        () ->
            ClientRegistrations.fromIssuerLocation(oidc.issuerUri())
                .registrationId(OidcRegistrations.SERVICE)
                .clientId(oidc.serviceClientId())
                .clientSecret(oidc.serviceClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    return new LazyClientRegistrationRepository(
        Map.of(OidcRegistrations.USER, user, OidcRegistrations.SERVICE, service));
  }

  /** Every connection to the database checks that it works as liv_bff (T16). */
  @Bean
  static BffConnectionCheck bffConnectionCheck() {
    return new BffConnectionCheck();
  }

  @Bean
  TokenCipher tokenCipher(BffProperties properties) {
    return TokenCipher.fromBase64(properties.session().encryptionKey());
  }

  @Bean
  EncryptedAuthorizedClientRepository authorizedClientRepository(
      TokenCipher cipher, ClientRegistrationRepository registrations) {
    return new EncryptedAuthorizedClientRepository(cipher, registrations);
  }

  @Bean
  ServiceTokens serviceTokens(ClientRegistrationRepository registrations) {
    return new ServiceTokens(registrations);
  }

  @Bean
  InternalApiClient internalApiClient(BffProperties properties, ServiceTokens serviceTokens) {
    BffProperties.Api api = properties.api();
    RestClient http =
        Http.client(api.connectTimeout(), api.readTimeout())
            .mutate()
            .baseUrl(api.baseUrl())
            .build();
    return new InternalApiClient(http, serviceTokens);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** The maximum duration of a session, 8 hours (001-10). */
  @Bean
  SessionLifetime sessionLifetime(BffProperties properties, Clock clock) {
    return new SessionLifetime(properties.session().absoluteTimeout(), clock);
  }

  /**
   * The token endpoint of the provider for the renewals. It is a bean so a test can replace it to
   * decide what the provider answers.
   */
  @Bean
  OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient() {
    return new RestClientRefreshTokenTokenResponseClient();
  }

  @Bean
  TokenRenewal tokenRenewal(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository clients,
      OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshClient) {
    return new TokenRenewal(registrations, clients, refreshClient);
  }

  /**
   * {@code GET /bff/session} answers before Spring Session's filter, so asking does not extend the
   * session (see {@link SessionStatusFilter}).
   */
  @Bean
  FilterRegistrationBean<SessionStatusFilter> sessionStatusFilter(
      SessionRepository<?> sessions,
      CookieSerializer cookieSerializer,
      SessionLifetime lifetime,
      Clock clock) {
    // The same cookie and serializer as Spring Session's filter, so both read the same id.
    CookieHttpSessionIdResolver idResolver = new CookieHttpSessionIdResolver();
    idResolver.setCookieSerializer(cookieSerializer);
    FilterRegistrationBean<SessionStatusFilter> registration =
        new FilterRegistrationBean<>(
            new SessionStatusFilter(sessions, idResolver, lifetime, clock));
    registration.addUrlPatterns(SessionStatusFilter.PATH);
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 10);
    return registration;
  }

  @Bean
  SessionCloser sessionCloser(
      EncryptedAuthorizedClientRepository clients,
      InternalApiClient api,
      RefreshTokenRevoker revoker) {
    return new SessionCloser(clients, api, revoker);
  }

  /**
   * The revocation reuses the timeouts of the calls to the API: both are short calls inside the
   * infrastructure.
   */
  @Bean
  RefreshTokenRevoker refreshTokenRevoker(BffProperties properties) {
    BffProperties.Api api = properties.api();
    return new RefreshTokenRevoker(
        Http.client(api.connectTimeout(), api.readTimeout()), properties.oidc().revocationUri());
  }
}
