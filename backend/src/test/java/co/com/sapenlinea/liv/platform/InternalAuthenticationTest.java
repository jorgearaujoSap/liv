package co.com.sapenlinea.liv.platform;

import static co.com.sapenlinea.liv.platform.ApiAuthenticationTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The internal routes only the BFF calls (ADR 0025): service token of {@code liv-bff-service} with
 * audience {@code liv-internal}, and on session start also the user token in {@code X-User-Token}.
 */
@DisplayName("Autenticación de /internal con el token de servicio (T12)")
class InternalAuthenticationTest extends PlatformWebTest {

  private static final String START = "/internal/v1/sessions";
  private static final String END = "/internal/v1/sessions/sid-1/end";
  private static final String USER_TOKEN = "X-User-Token";

  @Test
  @DisplayName("ADR 0025 · El BFF inicia la sesión con su token y el del usuario validado")
  void serviceAndUserTokens() {
    String user = user();
    HttpResponse<String> response =
        post(
            START,
            withUser(OidcTestProvider.serviceToken(), user),
            "{\"sid\":\"" + OidcTestProvider.sessionIdOf(user) + "\"}");

    assertThat(response.statusCode()).isEqualTo(201);
    // The filter handed the real subject to the use case: the session is the registered user's.
    Object registered =
        LivDatabase.readAsOwner(
                "SELECT id FROM core.admin_user WHERE identity_subject = ? AND identity_issuer = ?",
                ACTIVE,
                OidcTestProvider.issuer())
            .getFirst()[0];
    assertThat(response.body()).contains("\"userId\":\"" + registered + "\"");
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Sin token de servicio responde 401")
  void missingServiceToken() {
    assertRejected(post(END, Map.of()), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName(
      "001-14 · Un token de usuario con todos los alcances no sirve como token de servicio")
  void userTokenAsServiceToken() {
    String everything =
        OidcTestProvider.userToken(
            ACTIVE,
            LivDatabase.ADMIN_A,
            claims ->
                claims
                    .audience(
                        List.of(OidcTestProvider.USER_AUDIENCE, OidcTestProvider.SERVICE_AUDIENCE))
                    .claim("scope", "openid profile email liv-internal"));
    assertRejected(post(END, bearer(everything)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token de servicio de otro cliente responde 401")
  void otherServiceClient() {
    String other = OidcTestProvider.serviceToken(claims -> claims.claim("azp", "otro-cliente"));
    assertRejected(post(END, bearer(other)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token del cliente de servicio sin la audiencia interna responde 401")
  void serviceClientWithoutInternalAudience() {
    String token =
        OidcTestProvider.serviceToken(claims -> claims.audience(OidcTestProvider.USER_AUDIENCE));
    assertRejected(post(END, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName(
      "001-14 · Un token de servicio vencido, sin vencimiento o de otro emisor responde 401")
  void invalidServiceTokenClaims() {
    Instant past = Instant.now().minus(Duration.ofMinutes(15));
    String expired =
        OidcTestProvider.serviceToken(claims ->
            claims
                .issueTime(Date.from(past))
                .expirationTime(Date.from(past.plus(Duration.ofMinutes(5)))));
    String withoutExpiry = OidcTestProvider.serviceToken(claims -> claims.expirationTime(null));
    String otherIssuer =
        OidcTestProvider.serviceToken(
            claims -> claims.issuer(OidcTestProvider.issuer().replace("/liv", "/otro")));

    assertRejected(post(END, bearer(expired)), ErrorCodes.NOT_AUTHENTICATED);
    assertRejected(post(END, bearer(withoutExpiry)), ErrorCodes.NOT_AUTHENTICATED);
    assertRejected(post(END, bearer(otherIssuer)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token de servicio con otra firma o sin firma responde 401")
  void invalidServiceTokenSignature() {
    assertRejected(
        post(END, bearer(OidcTestProvider.serviceTokenSignedWithAnotherKey())),
        ErrorCodes.NOT_AUTHENTICATED);
    assertRejected(
        post(END, bearer(OidcTestProvider.unsignedServiceToken())), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un X-User-Token de más de 8.192 caracteres responde 401 aunque sea válido")
  void userTokenOverTheContractLimit() {
    String oversized =
        OidcTestProvider.userToken(
            ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("relleno", "x".repeat(8200)));
    assertThat(oversized.length()).isGreaterThan(8192);

    assertRejected(
        post(START, withUser(OidcTestProvider.serviceToken(), oversized)),
        ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @DisplayName("ADR 0025 · El cierre de sesión solo exige el token de servicio")
  void endWithServiceToken() {
    String body =
        "{\"issuer\":\"%s\",\"subject\":\"%s\",\"reason\":\"LOGOUT\"}"
            .formatted(OidcTestProvider.issuer(), ACTIVE);
    assertThat(post(END, bearer(OidcTestProvider.serviceToken()), body).statusCode())
        .isEqualTo(204);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Iniciar sesión sin X-User-Token responde 401")
  void missingUserToken() {
    assertRejected(
        post(START, bearer(OidcTestProvider.serviceToken())), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un X-User-Token con firma inválida responde 401")
  void invalidUserToken() {
    String forged = OidcTestProvider.userTokenSignedWithAnotherKey(ACTIVE, LivDatabase.ADMIN_A);
    assertRejected(
        post(START, withUser(OidcTestProvider.serviceToken(), forged)),
        ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un X-User-Token emitido para otro cliente (azp) responde 401")
  void userTokenOfAnotherClient() {
    String other =
        OidcTestProvider.userToken(
            ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("azp", "account-console"));
    assertRejected(
        post(START, withUser(OidcTestProvider.serviceToken(), other)),
        ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token de servicio no sirve como X-User-Token")
  void serviceTokenAsUserToken() {
    String service = OidcTestProvider.serviceToken();
    assertRejected(post(START, withUser(service, service)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @DisplayName("T11 · Las cabeceras admiten Authorization y un X-User-Token de 8.192 caracteres")
  void largestUserTokenFits() {
    String largest = "a".repeat(8192);
    HttpResponse<String> response = post(START, withUser(OidcTestProvider.serviceToken(), largest));

    assertRejected(response, ErrorCodes.NOT_AUTHENTICATED);
  }

  private static String user() {
    return OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
  }

  private static Map<String, String> withUser(String serviceToken, String userToken) {
    Map<String, String> headers = bearer(serviceToken);
    headers.put(USER_TOKEN, userToken);
    return headers;
  }
}
