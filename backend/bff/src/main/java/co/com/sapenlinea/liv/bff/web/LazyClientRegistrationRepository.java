package co.com.sapenlinea.liv.bff.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * The OIDC clients of the BFF, resolved the first time they are needed. Reading the discovery
 * document of the provider when the BFF starts would keep it from starting while the provider is
 * down (and from starting in any test without a provider). Here it only fails the request that
 * needs it, and the next one tries again: a failure is never kept.
 */
final class LazyClientRegistrationRepository implements ClientRegistrationRepository {

  private final Map<String, Supplier<ClientRegistration>> suppliers;
  private final Map<String, ClientRegistration> resolved = new ConcurrentHashMap<>();

  LazyClientRegistrationRepository(Map<String, Supplier<ClientRegistration>> suppliers) {
    this.suppliers = Map.copyOf(suppliers);
  }

  @Override
  public ClientRegistration findByRegistrationId(String registrationId) {
    Supplier<ClientRegistration> supplier = suppliers.get(registrationId);
    return supplier == null ? null : resolved.computeIfAbsent(registrationId, id -> supplier.get());
  }
}
