package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.sapenlinea.liv.bff.support.BffTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The access token lasts 5 minutes and the BFF renews it when less than a minute is left (plan,
 * «Sesión y tiempos»), at the moment a request of the person needs it. The provider of this test
 * issues tokens of 30 seconds, so they are always about to expire. If the provider refuses the
 * renewal ({@code invalid_grant}), the request is not forwarded without a token: the session ends
 * with {@code PROVIDER} and the answer is 401 (001-10).
 */
class ExpiringTokenTest extends BffTest {

  @DynamicPropertySource
  static void providerWithShortTokens(DynamicPropertyRegistry registry) {
    configure(registry, OidcProvider::issuerWithShortTokens);
  }

  @MockitoBean private OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refresh;

  @BeforeEach
  void providerRenews() {
    reset(refresh);
    when(refresh.getTokenResponse(any())).thenReturn(KeepAliveTest.renewed("acceso-renovado-2"));
  }

  @Test
  @Tag("001-11")
  @DisplayName(
      "001-11 · Si al token le queda menos de un minuto, se renueva antes de reenviar y se usa el nuevo")
  void aTokenAboutToExpireIsRenewedBeforeForwarding() {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();

    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(200);
    verify(refresh, times(1)).getTokenResponse(any());
    assertThat(BACKEND.apiCalls().get(0).header("authorization"))
        .isEqualTo("Bearer acceso-renovado-2");

    browser.get(bffBase() + "/api/v1/me");
    verify(refresh, times(1)).getTokenResponse(any());
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Si el proveedor rechaza la renovación (invalid_grant), /api responde 401, no reenvía y termina la sesión con PROVIDER")
  void invalidGrantAtForwardingEndsTheSession() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();
    when(refresh.getTokenResponse(any()))
        .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));

    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(BACKEND.apiCalls()).as("nunca se reenvía sin token").isEmpty();
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"PROVIDER\"");
    // The provider refused the grant: that refresh token is already worthless, there is nothing to
    // revoke.
    assertThat(BACKEND.revocationCalls()).isEmpty();
    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
  }
}
