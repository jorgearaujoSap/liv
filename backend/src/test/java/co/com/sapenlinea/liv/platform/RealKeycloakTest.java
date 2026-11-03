package co.com.sapenlinea.liv.platform;

import static co.com.sapenlinea.liv.platform.ApiAuthenticationTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 001-20 with the real provider (plan › ajustes de T12): the tokens Keycloak issues with the realm
 * as code have the shape the API expects — the user token of the BFF (audience {@code liv-api},
 * {@code azp = liv-bff}, flat claims, {@code typ: JWT}) and the service token of {@code
 * liv-bff-service} — pass its checks with its production configuration, and neither works in the
 * place of the other. Every rejected token belongs to a registered, active user, so only the token
 * checks can reject it (plan, «Pruebas de 001-14 y 001-20»).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PlatformWebTest.ApiProbe.class)
@DisplayName("Autenticación con Keycloak real y el realm como código (T12)")
class RealKeycloakTest {

  private static final String PROBE = "/api/v1/probe";
  private static final String START = "/internal/v1/sessions";
  private static final String END = "/internal/v1/sessions/sid-1/end";
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private static String userToken;
  private static String userTokenOfB;
  private static String serviceToken;
  private static String subjectOfUser;
  private static String sessionIdOfUser;

  @LocalServerPort int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
    registry.add("spring.datasource.password", LivDatabase::appPassword);
    registry.add("liv.security.issuer-uri", RealKeycloak::issuer);
  }

  /**
   * The users of Keycloak, registered in LIV as T13 will do on their first session: the user of A
   * in its administration, the user of B in A (its token says B), and the service account too.
   */
  @BeforeAll
  static void tokensOfTheRealProvider() throws ParseException {
    userToken = RealKeycloak.userToken(RealKeycloak.USER);
    userTokenOfB = RealKeycloak.userToken(RealKeycloak.USER_OF_B);
    serviceToken = RealKeycloak.serviceToken();
    subjectOfUser = claims(userToken).getSubject();
    sessionIdOfUser = claims(userToken).getStringClaim("sid");
    UUID administration = UUID.fromString(RealKeycloak.ADMINISTRATION);
    LivDatabase.registerAdministration(administration, "Ipiales (prueba)");
    // B exists too: the 401 of its user can only come from the mismatch with its registration.
    LivDatabase.registerAdministration(
        UUID.fromString(RealKeycloak.ADMINISTRATION_OF_B), "Administración B (prueba)");
    for (String token : new String[] {userToken, userTokenOfB, serviceToken}) {
      LivDatabase.registerUser(
          administration, RealKeycloak.issuer(), claims(token).getSubject(), "ACTIVE");
    }
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · El realm emite el token de usuario y el de servicio con la forma esperada")
  void tokensHaveTheExpectedShape() throws ParseException {
    SignedJWT user = SignedJWT.parse(userToken);
    JWTClaimsSet userClaims = user.getJWTClaimsSet();
    assertThat(user.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
    assertThat(userClaims.getAudience()).contains("liv-api").doesNotContain("liv-internal");
    assertThat(userClaims.getStringClaim("azp")).isEqualTo("liv-bff");
    assertThat(userClaims.getStringListClaim("roles")).contains("CONSULTA");
    assertThat(userClaims.getStringClaim("administration_id"))
        .isEqualTo(RealKeycloak.ADMINISTRATION);
    assertThat(userClaims.getClaims()).doesNotContainKey("realm_access");

    JWTClaimsSet service = claims(serviceToken);
    assertThat(service.getAudience()).contains("liv-internal").doesNotContain("liv-api");
    assertThat(service.getStringClaim("azp")).isEqualTo("liv-bff-service");
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · El token de usuario que Keycloak emite para el BFF entra en /api")
  void realUserTokenOnApi() {
    HttpResponse<String> response = get(PROBE, bearer(userToken));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"administrationId\":\"" + RealKeycloak.ADMINISTRATION + "\"")
        // The realm adds its default role too; LIV ignores roles outside its catalog (T14).
        .containsPattern("\"roles\":\"[^\"]*CONSULTA");
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · El token de servicio de Keycloak cierra una sesión en /internal")
  void realServiceTokenOnInternal() {
    String end =
        "{\"issuer\":\"%s\",\"subject\":\"%s\",\"reason\":\"LOGOUT\"}"
            .formatted(RealKeycloak.issuer(), subjectOfUser);
    assertThat(post(END, bearer(serviceToken), end).statusCode()).isEqualTo(204);
    assertRejected(post(END, Map.of()), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · El BFF inicia la sesión con los dos tokens reales")
  void realTokensStartASession() {
    Map<String, String> headers = bearer(serviceToken);
    headers.put("X-User-Token", userToken);

    String start = "{\"sid\":\"" + sessionIdOfUser + "\"}";
    assertThat(post(START, headers, start).statusCode()).isIn(200, 201);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · El token de servicio real no sirve en /api ni como X-User-Token")
  void realServiceTokenIsNotAUserToken() {
    assertRejected(get(PROBE, bearer(serviceToken)), ErrorCodes.NOT_AUTHENTICATED);

    Map<String, String> headers = bearer(serviceToken);
    headers.put("X-User-Token", serviceToken);
    assertRejected(post(START, headers), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · El token de usuario real no sirve como token de servicio")
  void realUserTokenIsNotAServiceToken() {
    assertRejected(post(END, bearer(userToken)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un token real con otra administración que la registrada responde 401")
  void realTokenOfAnotherAdministration() {
    assertRejected(get(PROBE, bearer(userTokenOfB)), ErrorCodes.ADMINISTRATION_MISMATCH);
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · El realm exige PKCE: sin desafío no hay código y otro verificador falla")
  void pkceIsEnforced() {
    assertThat(RealKeycloak.authorizeWithoutPkce())
        .startsWith("http://localhost:4200/login/oauth2/code/liv")
        .contains("error=invalid_request")
        .doesNotContain("code=");
    assertThat(RealKeycloak.exchangeWithAnotherVerifier(RealKeycloak.USER))
        .contains("invalid_grant")
        .containsIgnoringCase("PKCE");
  }

  private static JWTClaimsSet claims(String token) throws ParseException {
    return SignedJWT.parse(token).getJWTClaimsSet();
  }

  private HttpResponse<String> get(String path, Map<String, String> headers) {
    return send(HttpRequest.newBuilder(uri(path)).GET(), headers);
  }

  private HttpResponse<String> post(String path, Map<String, String> headers) {
    return post(path, headers, "{}");
  }

  private HttpResponse<String> post(String path, Map<String, String> headers, String body) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)),
        headers);
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static Map<String, String> bearer(String token) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    return headers;
  }

  private static HttpResponse<String> send(
      HttpRequest.Builder request, Map<String, String> headers) {
    headers.forEach(request::header);
    return RealKeycloak.send(HTTP, request);
  }
}
