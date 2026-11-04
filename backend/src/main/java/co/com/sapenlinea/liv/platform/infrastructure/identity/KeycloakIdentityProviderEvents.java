package co.com.sapenlinea.liv.platform.infrastructure.identity;

import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import java.time.Instant;
import java.util.List;

/** The events of Keycloak for the audit (001-18). Pending: T15. */
public final class KeycloakIdentityProviderEvents implements IdentityProviderEvents {

  private KeycloakIdentityProviderEvents() {}

  public static IdentityProviderEvents forProvider(
      String issuer, String clientId, String clientSecret, boolean allowHttp) {
    throw new UnsupportedOperationException("T15");
  }

  @Override
  public List<ProviderEvent> userEvents(Instant from, int max) {
    throw new UnsupportedOperationException("T15");
  }

  @Override
  public List<ProviderEvent> adminEvents(Instant from, int max) {
    throw new UnsupportedOperationException("T15");
  }
}
