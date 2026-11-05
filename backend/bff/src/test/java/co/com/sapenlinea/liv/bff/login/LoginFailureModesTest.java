package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.session.ServiceTokens;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.Browser.SignIn;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The sign in and the sign out when the pieces around the BFF fail in ways the HTTP status of the
 * API does not show: the identity provider does not issue the service token (the client secret was
 * rotated, or the provider is down). In every case the person ends up signed out and no session
 * with tokens is left in the database (001-05, 001-09, 001-11).
 */
class LoginFailureModesTest extends BffFlowTest {

  @MockitoBean private ServiceTokens serviceTokens;

  @BeforeEach
  void serviceTokenIssued() {
    when(serviceTokens.accessToken()).thenReturn("token-de-servicio-de-prueba");
  }

  private void providerRefusesTheServiceToken() {
    when(serviceTokens.accessToken())
        .thenThrow(
            new ClientAuthorizationException(new OAuth2Error("invalid_client"), "liv-bff-service"));
  }

  @Test
  @Tag("001-05")
  @DisplayName(
      "001-05 · Si no se obtiene el token de servicio, el inicio de sesión se rechaza y no queda nada guardado")
  void loginIsRejectedWhenTheServiceTokenFails() throws SQLException {
    providerRefusesTheServiceToken();
    Browser browser = browser();

    SignIn signIn = browser.signIn("/liquidaciones");

    assertThat(signIn.callback().status()).isEqualTo(302);
    assertThat(signIn.callback().location()).isEqualTo("/bff/login");
    assertThat(BACKEND.startSessionCalls()).isEmpty();
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
    assertThat(storedAttributes()).doesNotContain("eyJ");
  }

  @Test
  @Tag("001-09")
  @DisplayName(
      "001-09 · Si no se obtiene el token de servicio, salir igual revoca y cierra la sesión del BFF")
  void logoutWorksWhenTheServiceTokenFails() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");
    providerRefusesTheServiceToken();

    Reply reply = browser.post(bffBase() + "/bff/logout", "");

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.body().replaceAll("\\s", "")).isEqualTo("{\"redirectTo\":\"/bff/login\"}");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
  }

  private static String storedAttributes() throws SQLException {
    String stored =
        BffDatabase.queryValue(
            BffDatabase.jdbcUrl(),
            BffDatabase.OWNER,
            "SELECT string_agg(encode(attribute_bytes, 'escape'), '')"
                + " FROM bff.spring_session_attributes");
    return stored == null ? "" : stored;
  }
}
