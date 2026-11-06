package co.com.sapenlinea.liv.bff.logout;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * When the session ends in the provider (an administrator closes it or disables the person), the
 * provider tells the BFF at {@code /bff/backchannel-logout} (OpenID Connect Back-Channel Logout
 * 1.0, T19) and the session ends at once in the BFF and in LIV: EndSession with {@code PROVIDER}
 * and deletion. Only a logout token signed by the provider, for this client, with the event of the
 * standard and without {@code nonce}, is obeyed. The provider calls without a session cookie nor an
 * anti-CSRF token.
 */
@Tag("001-09")
class BackchannelLogoutTest extends BffFlowTest {

  private Reply backchannel(String logoutToken) {
    return new Browser(bffBase())
        .postWithoutCsrfToken(bffBase() + "/bff/backchannel-logout", "logout_token=" + logoutToken);
  }

  private static int oidcSessions() throws SQLException {
    return Integer.parseInt(
        BffDatabase.queryValue(
            BffDatabase.jdbcUrl(), BffDatabase.OWNER, "SELECT count(*) FROM bff.oidc_session"));
  }

  @Test
  @Tag("001-10")
  @Tag("001-18")
  @DisplayName(
      "001-09 · Cuando el proveedor cierra la sesión, el BFF la termina al instante con PROVIDER y la persona queda fuera")
  void theProviderEndsTheSession() throws SQLException {
    Browser person = browser();
    person.signIn("/");
    assertThat(oidcSessions()).as("el inicio registra el sid de la sesión").isEqualTo(1);
    BACKEND.reset();

    Reply reply = backchannel(OidcProvider.logoutToken(OidcProvider.LOGOUT));

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.header("Cache-Control")).contains("no-store");
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).path())
        .isEqualTo("/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end");
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"PROVIDER\"");
    assertThat(storedSessions()).isZero();
    assertThat(oidcSessions()).isZero();
    assertThat(person.get(bffBase() + "/api/v1/me").status()).isEqualTo(401);
  }

  @Test
  @DisplayName("001-09 · Un logout token de otra sesión del proveedor no termina esta")
  void anotherSessionOfTheProviderChangesNothing() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();

    Reply reply = backchannel(OidcProvider.logoutToken(OidcProvider.LOGOUT_OTHER_SESSION));

    assertThat(reply.status()).isEqualTo(200);
    assertThat(BACKEND.calls()).isEmpty();
    assertThat(storedSessions()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "001-09 · Se rechaza con 400, sin terminar nada, un logout token alterado, sin el evento, para otro cliente o con nonce")
  void aTokenThatIsNotValidIsRefused() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    String valid = OidcProvider.logoutToken(OidcProvider.LOGOUT);
    String[] parts = valid.split("\\.");
    String otherPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]))
                    .replace(OidcProvider.SESSION_ID, "sid-2")
                    .getBytes());
    String tampered = parts[0] + "." + otherPayload + "." + parts[2];

    for (String token :
        new String[] {
          tampered,
          OidcProvider.logoutToken(OidcProvider.LOGOUT_WITHOUT_EVENT),
          OidcProvider.logoutToken(OidcProvider.LOGOUT_OTHER_CLIENT),
          OidcProvider.logoutToken(OidcProvider.LOGOUT_WITH_NONCE),
          "no-es-un-jwt",
          ""
        }) {
      Reply reply = backchannel(token);
      assertThat(reply.status()).as(token).isEqualTo(400);
      assertThat(reply.body()).as(token).contains("invalid_request");
    }

    assertThat(BACKEND.calls()).isEmpty();
    assertThat(storedSessions()).isEqualTo(1);
  }

  @Test
  @DisplayName("001-09 · El canal trasero solo acepta POST")
  void onlyAPost() {
    Reply reply = new Browser(bffBase()).get(bffBase() + "/bff/backchannel-logout");

    assertThat(reply.status()).isIn(401, 404, 405);
  }
}
