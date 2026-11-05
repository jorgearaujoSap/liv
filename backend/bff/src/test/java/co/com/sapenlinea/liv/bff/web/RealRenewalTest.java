package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The renewal of the access token against a generic OIDC provider, with nothing replaced (001-20,
 * 001-11): the refresh grant reaches its token endpoint, the new token is the one forwarded to the
 * API, and the person's session goes on. The provider issues tokens of 30 seconds, so the first
 * request of the person already finds the token about to expire.
 */
class RealRenewalTest extends BffTest {

  @DynamicPropertySource
  static void providerWithShortTokens(DynamicPropertyRegistry registry) {
    configure(registry, OidcProvider::issuerWithShortTokens);
  }

  @Test
  @Tag("001-20")
  @DisplayName(
      "001-20 · El token por vencer se renueva contra el proveedor y la API recibe el nuevo")
  void theRefreshGrantReachesTheProvider() {
    Browser browser = browser();
    browser.signIn("/");
    String loginToken = BACKEND.startSessionCalls().get(0).header("x-user-token");
    BACKEND.reset();

    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(200);
    String forwarded = BACKEND.apiCalls().get(0).header("authorization");
    assertThat(forwarded).startsWith("Bearer ").isNotEqualTo("Bearer " + loginToken);
    assertThat(browser.get(bffBase() + "/bff/session").status()).isEqualTo(200);
  }
}
