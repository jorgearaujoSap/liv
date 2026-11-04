package co.com.sapenlinea.liv.platform.infrastructure.identity;

import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The adapter of the events of the identity provider, only where the importer runs. Its client is
 * {@code liv-audit-importer}, with its own secret: the BFF's clients never read events (plan step
 * 9). Nothing is called at startup: the API starts even if the provider is down.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "liv.audit.importer.enabled", havingValue = "true")
class IdentityProviderEventsConfiguration {

  @Bean
  IdentityProviderEvents identityProviderEvents(
      @Value("${liv.security.issuer-uri}") String issuer,
      @Value("${liv.security.allow-http-issuer:false}") boolean allowHttp,
      @Value("${liv.audit.importer.client-id}") String clientId,
      @Value("${liv.audit.importer.client-secret:}") String clientSecret) {
    return KeycloakIdentityProviderEvents.forProvider(issuer, clientId, clientSecret, allowHttp);
  }
}
