package co.com.sapenlinea.liv.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * The keys come from the {@code jwks_uri} the provider publishes, not from the issuer text: without
 * the explicit permission, no call to the provider may go over plain http (doc. 02 §3, T12).
 */
@DisplayName("Llamadas al proveedor de identidad (T12)")
class TokenDecodersTest {

  @Test
  @DisplayName("Doc. 02 §3 · Sin el permiso, el descubrimiento y el JWKS por http se rechazan")
  void refusesPlainHttp() {
    assertThatThrownBy(() ->
            TokenDecoders.provider(false).getForObject("http://localhost:1/jwks", String.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https");
  }

  @Test
  @DisplayName("Doc. 02 §3 · Los decodificadores no llaman por http sin el permiso configurado")
  void decodersFollowTheConfiguredPermission() {
    LivSecurityProperties withoutPermission =
        new LivSecurityProperties(
            "http://localhost:1/realms/liv",
            false,
            "liv-api",
            "liv-bff",
            "liv-internal",
            "liv-bff-service",
            new LivSecurityProperties.Claims("administration_id", "roles"));

    assertThatThrownBy(() -> TokenDecoders.userTokens(withoutPermission).decode("token"))
        .hasStackTraceContaining("must be called over https");
    assertThatThrownBy(() -> TokenDecoders.serviceTokens(withoutPermission).decode("token"))
        .hasStackTraceContaining("must be called over https");
  }

  @Test
  @DisplayName("Doc. 02 §3 · Con el permiso (dev y pruebas), http llega al proveedor")
  void allowsPlainHttpWithThePermission() {
    assertThatThrownBy(() ->
            TokenDecoders.provider(true).getForObject("http://localhost:1/jwks", String.class))
        .isInstanceOf(ResourceAccessException.class);
  }
}
