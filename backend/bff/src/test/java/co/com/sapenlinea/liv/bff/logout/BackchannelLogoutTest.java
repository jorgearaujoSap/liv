package co.com.sapenlinea.liv.bff.logout;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.LivBffApplication;
import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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

  /** A logout token the provider did issue: a test that gets none proves nothing. */
  private static String tokenOf(String kind) {
    String token = OidcProvider.logoutToken(kind);
    assertThat(token).as(kind).startsWith("eyJ");
    return token;
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

    Reply reply = backchannel(tokenOf(OidcProvider.LOGOUT));

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.header("Cache-Control")).contains("no-store");
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).path())
        .isEqualTo("/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end");
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"PROVIDER\"");
    assertThat(BACKEND.revocationCalls()).as("se revoca su refresh token").hasSize(1);
    assertThat(storedSessions()).isZero();
    assertThat(oidcSessions()).isZero();
    assertThat(person.get(bffBase() + "/api/v1/me").status()).isEqualTo(401);
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-09 · Si LIV no acepta el fin, la sesión del BFF se borra igual y el aviso a LIV queda pendiente")
  void aFailedEndInLivIsLeftPending() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    BACKEND.endSessionAnswers(503);

    Reply reply = backchannel(tokenOf(OidcProvider.LOGOUT));

    assertThat(reply.status()).isEqualTo(200);
    assertThat(storedSessions()).as("no se puede volver a usar").isZero();
    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT count(*) FROM bff.oidc_session WHERE pending_end_since IS NOT NULL"))
        .as("el aviso a LIV queda para el barrido")
        .isEqualTo("1");
  }

  @Test
  @DisplayName(
      "001-09 · Si una petición de la persona tiene la sesión bloqueada, el cierre la espera y la termina")
  void aSessionLockedByARequestIsWaitedFor() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    String token = tokenOf(OidcProvider.LOGOUT);

    Reply reply;
    try (Connection request =
        DriverManager.getConnection(
            BffDatabase.jdbcUrl(), BffDatabase.APP, BffDatabase.password(BffDatabase.APP))) {
      request.setAutoCommit(false);
      try (Statement lock = request.createStatement()) {
        lock.execute("SELECT 1 FROM bff.spring_session FOR UPDATE");
      }
      Thread release =
          new Thread(() -> {
            pause(1500);
            try {
              request.rollback();
            } catch (SQLException e) {
              throw new IllegalStateException(e);
            }
          });
      release.start();
      reply = backchannel(token);
      release.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }

    assertThat(reply.status()).isEqualTo(200);
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @DisplayName(
      "001-09 · Si la sesión sigue bloqueada más de 5 segundos, responde 503 y no toca nada, para que el proveedor reintente")
  void aSessionLockedForTooLongIsLeftForARetry() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    String token = tokenOf(OidcProvider.LOGOUT);

    Reply reply;
    try (Connection request =
        DriverManager.getConnection(
            BffDatabase.jdbcUrl(), BffDatabase.APP, BffDatabase.password(BffDatabase.APP))) {
      request.setAutoCommit(false);
      try (Statement lock = request.createStatement()) {
        lock.execute("SELECT 1 FROM bff.spring_session FOR UPDATE");
      }
      reply = backchannel(token);
      request.rollback();
    }

    assertThat(reply.status()).isEqualTo(503);
    assertThat(BACKEND.calls()).isEmpty();
    assertThat(storedSessions()).isEqualTo(1);
    assertThat(oidcSessions()).as("el sid sigue, para el reintento").isEqualTo(1);
  }

  @Test
  @DisplayName("001-09 · Salir también borra el registro del sid de la sesión")
  void signingOutForgetsTheSid() throws SQLException {
    Browser person = browser();
    person.signIn("/");

    assertThat(person.post(bffBase() + "/bff/logout", "").status()).isEqualTo(200);

    assertThat(oidcSessions()).isZero();
  }

  @Test
  @DisplayName(
      "001-09 · Otra instancia del BFF termina la sesión abierta en esta: lo compartido es la base")
  void anotherInstanceEndsTheSession() throws SQLException {
    Browser person = browser();
    person.signIn("/");
    BACKEND.reset();

    try (ConfigurableApplicationContext other =
        new SpringApplicationBuilder(LivBffApplication.class)
            .profiles("test")
            // As arguments, which come before application.yml; .properties() would come after.
            .run(
                java.util.stream.Stream.concat(
                        configurationOf(OidcProvider.issuer()).entrySet().stream()
                            .map(property -> "--" + property.getKey() + "=" + property.getValue()),
                        java.util.stream.Stream.of("--server.port=0"))
                    .toArray(String[]::new))) {
      String otherBase =
          "http://localhost:" + other.getEnvironment().getProperty("local.server.port");
      Reply reply =
          new Browser(otherBase)
              .postWithoutCsrfToken(
                  otherBase + "/bff/backchannel-logout",
                  "logout_token=" + OidcProvider.logoutToken(OidcProvider.LOGOUT));

      assertThat(reply.status()).isEqualTo(200);
    }
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(person.get(bffBase() + "/api/v1/me").status())
        .as("en esta instancia ya no sirve")
        .isEqualTo(401);
  }

  @Test
  @DisplayName("001-09 · Un logout token de otra sesión del proveedor no termina esta")
  void anotherSessionOfTheProviderChangesNothing() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();

    Reply reply = backchannel(tokenOf(OidcProvider.LOGOUT_OTHER_SESSION));

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
    String valid = tokenOf(OidcProvider.LOGOUT);
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
          tokenOf(OidcProvider.LOGOUT_WITHOUT_EVENT),
          tokenOf(OidcProvider.LOGOUT_OTHER_CLIENT),
          tokenOf(OidcProvider.LOGOUT_WITH_NONCE),
          tokenOf(OidcProvider.LOGOUT_OLD),
          "x".repeat(20_000),
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
  @DisplayName("001-09 · El canal trasero solo acepta un POST con un formulario")
  void onlyAFormPost() {
    Browser provider = new Browser(bffBase());

    assertThat(provider.get(bffBase() + "/bff/backchannel-logout").status()).isEqualTo(405);
    assertThat(
            provider
                .post(
                    bffBase() + "/bff/backchannel-logout",
                    "{}",
                    java.util.Map.of("Content-Type", "application/json"))
                .status())
        .isEqualTo(415);
  }
}
