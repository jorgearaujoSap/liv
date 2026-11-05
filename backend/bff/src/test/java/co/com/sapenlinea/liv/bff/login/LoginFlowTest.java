package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.Browser.SignIn;
import co.com.sapenlinea.liv.bff.support.FakeBackend.Call;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The sign in of the BFF as a person lives it, against a generic OIDC provider (mock-oauth2-server)
 * and a fake liv-api: authorization code with PKCE, the new session id, StartSession with both
 * tokens, and the tokens stored encrypted.
 */
class LoginFlowTest extends BffFlowTest {

  @Test
  @Tag("001-20")
  @Tag("001-01")
  @DisplayName(
      "001-20 · /bff/login envía a la persona al proveedor con código, PKCE, nonce y prompt=login")
  void loginSendsThePersonToTheProvider() {
    Reply start = browser().get(bffBase() + "/bff/login");

    assertThat(start.status()).isEqualTo(302);
    URI authorize = URI.create(start.location());
    assertThat(authorize.toString()).startsWith(OidcProvider.issuer() + "/authorize?");
    Map<String, String> query = query(authorize);
    assertThat(query)
        .containsEntry("response_type", "code")
        .containsEntry("client_id", OidcProvider.USER_CLIENT)
        .containsEntry("code_challenge_method", "S256")
        .containsEntry("prompt", "login")
        .containsKeys("state", "nonce", "code_challenge");
    assertThat(query.get("scope")).contains("openid");
    assertThat(query.get("redirect_uri")).isEqualTo(bffBase() + "/login/oauth2/code/liv");
  }

  @Test
  @Tag("001-20")
  @Tag("001-01")
  @DisplayName("001-20 · Flujo completo: la persona se autentica y vuelve a la ruta que pidió")
  void aPersonSignsInAndComesBackToTheRouteRequested() {
    SignIn signIn = browser().signIn("/liquidaciones?anio=2026");

    assertThat(signIn.callback().status()).isEqualTo(302);
    assertThat(signIn.callback().location()).isEqualTo("/liquidaciones?anio=2026");
    assertThat(BACKEND.startSessionCalls()).hasSize(1);
  }

  @Test
  @Tag("001-01")
  @DisplayName("001-01 · Sin returnTo, la persona vuelve al inicio")
  void withoutReturnToTheCallbackGoesHome() {
    assertThat(browser().signIn(null).callback().location()).isEqualTo("/");
  }

  @ParameterizedTest
  @Tag("001-01")
  @ValueSource(
      strings = {
        "https://evil.example/robo",
        "//evil.example",
        "/\\evil.example",
        "javascript:alert(1)",
        "/%2F/evil.example",
      })
  @DisplayName("001-01 · Un returnTo que sale de LIV no se obedece (redirección abierta)")
  void anOpenRedirectIsNotFollowed(String returnTo) {
    SignIn signIn = browser().signIn(returnTo);

    assertThat(signIn.callback().status()).isEqualTo(302);
    assertThat(signIn.callback().location()).isEqualTo("/");
  }

  @Test
  @Tag("001-11")
  @DisplayName("001-11 · Al autenticarse cambia el id de sesión y el anterior deja de existir")
  void theSessionIdChangesWhenThePersonAuthenticates() throws SQLException {
    Browser browser = browser();
    Reply start = browser.get(bffBase() + "/bff/login");
    String idBefore = browser.cookie("LIV_SESSION");
    assertThat(idBefore).isNotBlank();

    Reply form = browser.post(start.location(), "username=" + OidcProvider.SUBJECT);
    browser.get(form.location());
    String idAfter = browser.cookie("LIV_SESSION");

    assertThat(idAfter).isNotBlank().isNotEqualTo(idBefore);
    assertThat(storedSessions()).isEqualTo(1);
    assertThat(sessionIdsInTheDatabase())
        .containsExactly(decode(idAfter))
        .doesNotContain(decode(idBefore));
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · StartSession recibe el token de servicio, el del usuario y el sid")
  void startSessionReceivesBothTokens() {
    browser().signIn("/");

    List<Call> calls = BACKEND.startSessionCalls();
    assertThat(calls).hasSize(1);
    Call call = calls.get(0);
    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.header("content-type")).startsWith("application/json");
    assertThat(call.body()).contains("\"sid\":\"" + OidcProvider.SESSION_ID + "\"");

    String service = call.header("authorization");
    assertThat(service).startsWith("Bearer ");
    assertThat(claims(service.substring("Bearer ".length())))
        .contains("liv-internal")
        .contains(OidcProvider.SERVICE_CLIENT);

    String user = call.header("x-user-token");
    assertThat(user).isNotBlank();
    assertThat(claims(user)).contains(OidcProvider.SUBJECT).contains(OidcProvider.SESSION_ID);
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · Un 200 de StartSession es un reintento con la sesión ya creada: entra")
  void anAlreadyCreatedSessionIsNotAFailure() {
    BACKEND.startSessionAnswers(200);

    SignIn signIn = browser().signIn("/inicio");

    assertThat(signIn.callback().location()).isEqualTo("/inicio");
  }

  @ParameterizedTest
  @Tag("001-05")
  @Tag("001-17")
  @ValueSource(ints = {401, 403, 500})
  @DisplayName(
      "001-05 · Si StartSession falla, revoca el token, borra la sesión y vuelve a /bff/login")
  void aRejectedSessionIsRevokedAndSentBackToLogin(int status) throws SQLException {
    BACKEND.startSessionAnswers(status);
    Browser browser = browser();

    SignIn signIn = browser.signIn("/liquidaciones");

    assertThat(signIn.callback().status()).isEqualTo(302);
    assertThat(signIn.callback().location()).isEqualTo("/bff/login");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    Call revocation = BACKEND.revocationCalls().get(0);
    assertThat(revocation.body())
        .contains("token_type_hint=refresh_token")
        .containsPattern("token=[^&]+");
    assertThat(revocation.header("authorization"))
        .isEqualTo(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString(
                        "liv-bff:secreto-de-prueba-del-bff".getBytes(StandardCharsets.UTF_8)));
    assertThat(storedSessions()).as("sesiones guardadas").isZero();
    assertThat(browser.cookie("LIV_SESSION")).as("cookie de sesión").isNull();
    // A refusal (4xx) is a clear answer: LIV has no session to end. After a failure with no clear
    // answer (5xx) LIV may have created it, so the BFF compensates with EndSession.
    if (status >= 500) {
      assertThat(BACKEND.endSessionCalls()).hasSize(1);
      assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"PROVIDER\"");
    } else {
      assertThat(BACKEND.endSessionCalls()).as("EndSession de una sesión que no empezó").isEmpty();
    }
    assertThat(revocation.body())
        .as("se revoca el refresh token")
        .contains("token=" + OidcProvider.UNSIGNED_JWT_HEADER);
  }

  @Test
  @Tag("001-05")
  @DisplayName("001-05 · El rechazo no lleva ningún parámetro que diga el motivo")
  void theRejectionCarriesNoReason() {
    BACKEND.startSessionAnswers(403);

    SignIn signIn = browser().signIn("/");

    assertThat(signIn.callback().location()).doesNotContain("?").doesNotContain("reason");
    assertThat(signIn.callback().body()).isNullOrEmpty();
  }

  @Test
  @Tag("001-05")
  @DisplayName("001-05 · Una falla al revocar no impide borrar la sesión ni volver a /bff/login")
  void aFailedRevocationStillClosesTheLocalSession() throws SQLException {
    BACKEND.startSessionAnswers(403);
    BACKEND.revocationAnswers(500);

    SignIn signIn = browser().signIn("/");

    assertThat(signIn.callback().location()).isEqualTo("/bff/login");
    assertThat(storedSessions()).isZero();
  }

  @Test
  @Tag("001-11")
  @DisplayName(
      "001-11 · Ninguna respuesta del BFF contiene un JWT, y solo se usa la cookie LIV_SESSION")
  void noResponseCarriesAToken() {
    Browser browser = browser();
    browser.signIn("/inicio");
    browser.get(bffBase() + "/bff/login");
    browser.get(bffBase() + "/privado");

    assertThat(browser.bffReplies()).isNotEmpty();
    for (Reply reply : browser.bffReplies()) {
      String everything = reply.headers().toString() + reply.body();
      assertThat(JWT.matcher(everything).find()).as("JWT en %s", reply.headers()).isFalse();
      assertThat(everything.toLowerCase(Locale.ROOT)).doesNotContain("id_token_hint");
    }
    List<String> cookieNames =
        browser.bffReplies().stream()
            .flatMap(reply -> reply.setCookies().stream())
            .map(setCookie -> setCookie.split("=", 2)[0])
            .distinct()
            .toList();
    assertThat(cookieNames).containsExactly("LIV_SESSION");
    browser.bffReplies().stream()
        .flatMap(reply -> reply.setCookies().stream())
        .forEach(cookie ->
            assertThat(cookie.toLowerCase(Locale.ROOT))
                .as(cookie)
                .contains("httponly", "secure", "samesite=strict"));
  }

  @Test
  @Tag("001-11")
  @DisplayName("001-11 · La cookie de sesión es HttpOnly, Secure y SameSite=Strict")
  void theSessionCookieIsHardened() {
    SignIn signIn = browser().signIn("/");

    List<String> cookies = signIn.callback().setCookies();
    assertThat(cookies).hasSize(1);
    String cookie = cookies.get(0);
    assertThat(cookie).startsWith("LIV_SESSION=");
    assertThat(Arrays.stream(cookie.split(";")).map(String::trim).collect(Collectors.toSet()))
        .contains("HttpOnly", "Secure", "SameSite=Strict")
        .contains("Path=/");
  }

  @Test
  @Tag("001-11")
  @DisplayName("001-11 · Los tokens se guardan cifrados: la base de datos no contiene ninguno")
  void tokensAreStoredEncrypted() throws SQLException {
    browser().signIn("/");
    String userToken = BACKEND.startSessionCalls().get(0).header("x-user-token");

    String stored =
        BffDatabase.queryValue(
            BffDatabase.jdbcUrl(),
            BffDatabase.OWNER,
            "SELECT string_agg(encode(attribute_bytes, 'escape'), '') FROM bff.spring_session_attributes");

    assertThat(stored).as("atributos guardados").isNotBlank();
    assertThat(stored).doesNotContain(userToken);
    assertThat(JWT.matcher(stored).find()).as("un JWT (id, acceso o refresh) en claro").isFalse();
    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT principal_name FROM bff.spring_session"))
        .as("nombre de la persona en la sesión")
        .isEqualTo(OidcProvider.SUBJECT);
  }

  @Test
  @Tag("001-01")
  @DisplayName("001-01 · Un regreso del proveedor sin un inicio previo vuelve a /bff/login")
  void aCallbackWithoutAStartedLoginGoesBackToLogin() {
    Reply reply =
        browser().get(bffBase() + "/login/oauth2/code/liv?code=inventado&state=inventado");

    assertThat(reply.status()).isEqualTo(302);
    assertThat(reply.location()).isEqualTo("/bff/login");
    assertThat(BACKEND.startSessionCalls()).isEmpty();
  }

  @Test
  @Tag("001-11")
  @DisplayName(
      "001-11 · Una ruta que no es del inicio de sesión, sin sesión, responde 401 sin redirigir")
  void aProtectedRouteWithoutASessionIsUnauthorized() {
    Reply reply = browser().get(bffBase() + "/privado");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(reply.location()).isNull();
  }

  private static Map<String, String> query(URI uri) {
    return Arrays.stream(uri.getRawQuery().split("&"))
        .map(pair -> pair.split("=", 2))
        .collect(
            Collectors.toMap(
                pair -> pair[0],
                pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
  }

  private static String claims(String jwt) {
    return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
  }

  /** The cookie carries the session id in base64 (Spring Session); the database, as it is. */
  private static String decode(String cookie) {
    return new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
  }

  private static List<String> sessionIdsInTheDatabase() throws SQLException {
    return List.of(
        BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT string_agg(session_id, ',') FROM bff.spring_session")
            .split(","));
  }
}
