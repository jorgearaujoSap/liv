package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * The BFF does not start with a configuration that would leave it insecure or half working (secrets
 * and URLs have no default value, the issuer must use https), and it starts without the identity
 * provider: the clients are resolved when first needed and a failure is not kept.
 */
@Tag("001-20")
class BffConfigurationTest {

  private static BffProperties.Oidc oidc(String issuer, boolean allowHttp, String secret) {
    return new BffProperties.Oidc(
        issuer, allowHttp, "liv-bff", secret, "liv-bff-service", "secreto", null, "{baseUrl}/x");
  }

  @Test
  @DisplayName("001-20 · El emisor debe ser https, salvo que se permita http para desarrollo")
  void theIssuerMustBeHttps() {
    assertThatThrownBy(() -> oidc("http://auth.liv.test/realms/liv", false, "s"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("https");
    assertThat(oidc("https://auth.liv.test/realms/liv", false, "s").issuerUri()).isNotBlank();
    assertThat(oidc("http://localhost:8180/realms/liv", true, "s").allowHttpIssuer()).isTrue();
  }

  @Test
  @DisplayName(
      "001-20 · Sin emisor, sin secretos, sin URL de la API o sin clave de sesión no arranca")
  void secretsAndUrlsHaveNoDefault() {
    assertThatThrownBy(() -> oidc(null, true, "s")).hasMessageContaining("issuer-uri");
    assertThatThrownBy(() -> oidc("https://a", false, " ")).hasMessageContaining("client-secret");
    assertThatThrownBy(() ->
            new BffProperties.Oidc("https://a", false, "c", "s", "svc", null, null, "{baseUrl}/x"))
        .hasMessageContaining("service-client-secret");
    assertThatThrownBy(() -> new BffProperties.Api(null, null, null))
        .hasMessageContaining("base-url");
    assertThatThrownBy(() -> new BffProperties.Session("")).hasMessageContaining("encryption-key");
    assertThatThrownBy(() -> new BffProperties(null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("001-20 · Los clientes se resuelven al primer uso y un fallo no se guarda")
  void registrationsAreResolvedLazilyAndFailuresAreNotKept() {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("liv")
            .clientId("c")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("http://localhost/token")
            .build();
    AtomicInteger attempts = new AtomicInteger();
    var registrations =
        new LazyClientRegistrationRepository(
            Map.of(
                "liv",
                () -> {
                  if (attempts.incrementAndGet() == 1) {
                    throw new IllegalArgumentException("proveedor caído");
                  }
                  return registration;
                }));

    assertThat(attempts).as("nada se lee al crearlo").hasValue(0);
    assertThatThrownBy(() -> registrations.findByRegistrationId("liv"))
        .hasMessageContaining("proveedor caído");
    assertThat(registrations.findByRegistrationId("liv")).isSameAs(registration);
    assertThat(registrations.findByRegistrationId("liv")).isSameAs(registration);
    assertThat(attempts).as("el éxito sí se guarda").hasValue(2);
    assertThat(registrations.findByRegistrationId("otro")).isNull();
  }
}
