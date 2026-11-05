package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
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
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code POST /bff/session/keepalive}: the person chose to stay connected (001-22). It always
 * renews the access token, even if it is not about to expire, so the session of the identity
 * provider also starts counting its inactivity again (plan, «Sesión y tiempos»), and it extends the
 * session of the BFF. If the provider refuses the renewal ({@code invalid_grant}: the user was
 * disabled or the session was closed there) the session ends with {@code PROVIDER} and the answer
 * is 401. The provider's token endpoint is replaced by a mock to decide what it answers.
 */
class KeepAliveTest extends BffFlowTest {

  @MockitoBean private OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refresh;

  @BeforeEach
  void providerRenews() {
    reset(refresh);
    when(refresh.getTokenResponse(any())).thenReturn(renewed("acceso-renovado-1"));
  }

  static OAuth2AccessTokenResponse renewed(String accessToken) {
    return OAuth2AccessTokenResponse.withToken(accessToken)
        .tokenType(OAuth2AccessToken.TokenType.BEARER)
        .expiresIn(300)
        .refreshToken("refresco-renovado")
        .build();
  }

  private Browser signedIn() {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();
    return browser;
  }

  @Test
  @Tag("001-22")
  @DisplayName(
      "001-22 · keepalive renueva siempre el token, aunque no esté por vencer, y responde lo que queda de sesión")
  void keepAliveAlwaysRenews() {
    Browser browser = signedIn();

    Reply first = browser.post(bffBase() + "/bff/session/keepalive", "");
    Reply second = browser.post(bffBase() + "/bff/session/keepalive", "");

    assertThat(first.status()).isEqualTo(200);
    assertThat(SessionStatusTest.seconds(first.body(), "idleSecondsLeft")).isBetween(880L, 900L);
    assertThat(SessionStatusTest.seconds(first.body(), "absoluteSecondsLeft")).isPositive();
    assertThat(second.status()).isEqualTo(200);
    verify(refresh, times(2)).getTokenResponse(any());
    assertThat(first.header("cache-control")).contains("no-store");
    assertThat(JWT.matcher(first.headers().toString() + first.body()).find()).isFalse();
  }

  @Test
  @Tag("001-22")
  @DisplayName("001-22 · Tras keepalive, /api usa el token renovado")
  void theRenewedTokenIsTheOneUsed() {
    Browser browser = signedIn();

    browser.post(bffBase() + "/bff/session/keepalive", "");
    browser.get(bffBase() + "/api/v1/me");

    assertThat(BACKEND.apiCalls()).hasSize(1);
    assertThat(BACKEND.apiCalls().get(0).header("authorization"))
        .isEqualTo("Bearer acceso-renovado-1");
    verify(refresh, times(1)).getTokenResponse(any());
  }

  @Test
  @Tag("001-22")
  @DisplayName("001-22 · keepalive sin sesión responde 401 y no renueva nada")
  void withoutASession() {
    Browser browser = browser();
    browser.get(bffBase() + "/api/v1/me");

    Reply reply = browser.post(bffBase() + "/bff/session/keepalive", "");

    assertThat(reply.status()).isEqualTo(401);
    verify(refresh, times(0)).getTokenResponse(any());
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Si el proveedor rechaza la renovación (invalid_grant), responde 401 y termina la sesión con PROVIDER")
  void invalidGrantEndsTheSession() throws SQLException {
    Browser browser = signedIn();
    when(refresh.getTokenResponse(any()))
        .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));

    Reply reply = browser.post(bffBase() + "/bff/session/keepalive", "");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).body())
        .contains("\"reason\":\"PROVIDER\"")
        .contains("\"subject\":\"" + OidcProvider.SUBJECT + "\"");
    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
    assertThat(reply.body()).doesNotContain("invalid_grant");
  }
}
