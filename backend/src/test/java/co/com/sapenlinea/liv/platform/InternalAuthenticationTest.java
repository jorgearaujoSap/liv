package co.com.sapenlinea.liv.platform;

import static co.com.sapenlinea.liv.platform.ApiAuthenticationTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.http.HttpResponse;
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
    HttpResponse<String> response = post(START, withUser(OidcTestProvider.serviceToken(), user()));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains(ACTIVE);
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
  @DisplayName("ADR 0025 · El cierre de sesión solo exige el token de servicio")
  void endWithServiceToken() {
    assertThat(post(END, bearer(OidcTestProvider.serviceToken())).statusCode()).isEqualTo(204);
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
