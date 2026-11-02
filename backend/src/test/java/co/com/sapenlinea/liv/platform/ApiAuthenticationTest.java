package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 001-14, 001-05, 001-17 and 001-20 on {@code /api/**}. Every rejected token belongs to a
 * registered, active user, so a 401 here comes from the token and not from the registry.
 */
@DisplayName("Autenticación de /api con un proveedor OIDC genérico (T12)")
class ApiAuthenticationTest extends PlatformWebTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PROBE = "/api/v1/probe";

  @Test
  @Tag("001-20")
  @DisplayName(
      "001-20 · Un token válido de un emisor OIDC genérico entra con la administración registrada")
  void validTokenFromGenericIssuer() {
    HttpResponse<String> response =
        get(PROBE, bearer(OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A)));

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("subject").asString()).isEqualTo(ACTIVE);
    assertThat(body.get("administrationId").asString()).isEqualTo(LivDatabase.ADMIN_A.toString());
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Sin token responde 401 sin datos")
  void missingToken() {
    assertRejected(get(PROBE, Map.of()), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token vencido responde 401")
  void expiredToken() {
    Instant past = Instant.now().minus(Duration.ofMinutes(15));
    String token =
        OidcTestProvider.userToken(
            ACTIVE,
            LivDatabase.ADMIN_A,
            claims ->
                claims
                    .issueTime(Date.from(past))
                    .expirationTime(Date.from(past.plus(Duration.ofMinutes(5)))));
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token de otra audiencia responde 401")
  void otherAudience() {
    String token =
        OidcTestProvider.userToken(
            ACTIVE, LivDatabase.ADMIN_A, claims -> claims.audience("otra-aplicacion"));
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token de otro emisor responde 401")
  void otherIssuer() {
    String token =
        OidcTestProvider.userToken(
            ACTIVE,
            LivDatabase.ADMIN_A,
            claims -> claims.issuer(OidcTestProvider.issuer().replace("/liv", "/otro")));
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token con firma inválida responde 401")
  void invalidSignature() {
    String token = OidcTestProvider.userTokenSignedWithAnotherKey(ACTIVE, LivDatabase.ADMIN_A);
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token sin firma (alg: none) responde 401")
  void unsignedToken() {
    String token = OidcTestProvider.unsignedUserToken(ACTIVE, LivDatabase.ADMIN_A);
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token de servicio no sirve en /api")
  void serviceTokenOnApi() {
    // Registered subject and administration: only the audience can reject it.
    String token =
        OidcTestProvider.serviceToken(claims ->
            claims.subject(ACTIVE).claim("administration_id", LivDatabase.ADMIN_A.toString()));
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token sin vencimiento (sin exp) responde 401")
  void tokenWithoutExpiry() {
    String token =
        OidcTestProvider.userToken(
            ACTIVE, LivDatabase.ADMIN_A, claims -> claims.expirationTime(null));
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un usuario sin registro en LIV responde 401 (no hay sesión iniciada)")
  void unregisteredUser() {
    String token = OidcTestProvider.userToken(UNREGISTERED, LivDatabase.ADMIN_A);
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-05")
  @DisplayName("001-05 · Un usuario desactivado en LIV responde 401 aunque su token sea válido")
  void inactiveUser() {
    String token = OidcTestProvider.userToken(INACTIVE, LivDatabase.ADMIN_A);
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.USER_INACTIVE);
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un token con otra administración que la registrada responde 401")
  void administrationMismatch() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_B);
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.ADMINISTRATION_MISMATCH);
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un token sin administración responde 401")
  void missingAdministration() {
    String token =
        OidcTestProvider.userToken(
            ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("administration_id", null));
    assertRejected(get(PROBE, bearer(token)), ErrorCodes.ADMINISTRATION_MISMATCH);
  }

  /** 401 with Problem Details that carry only the code: no user data, no token details. */
  static void assertRejected(HttpResponse<String> response, String code) {
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("code").asString()).isEqualTo(code);
    assertThat(body.get("status").asInt()).isEqualTo(401);
    // RFC 9457 §4.2.1: without "type" the type is about:blank.
    assertThat(body.propertyNames())
        .containsExactlyInAnyOrder("title", "status", "instance", "code");
  }
}
