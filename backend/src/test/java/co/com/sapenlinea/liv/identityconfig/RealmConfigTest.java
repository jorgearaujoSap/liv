package co.com.sapenlinea.liv.identityconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Verifica la configuración del realm de Keycloak como código (tarea T07 de la feature 001). Lee
 * los archivos de identity/config y resuelve sus variables con los valores de producción, como lo
 * hará keycloak-config-cli.
 */
@DisplayName("Realm liv de Keycloak (001 T07)")
class RealmConfigTest {

  private static final Path BASE = Path.of("identity/config/base/liv-realm.yaml");
  private static final Path DEV = Path.of("identity/config/dev/liv-realm-dev.yaml");
  private static final Pattern VAR = Pattern.compile("\\$\\(env:([A-Z0-9_]+)(?::-([^)]*))?\\)");
  private static final Set<String> SECRETS =
      Set.of("LIV_BFF_CLIENT_SECRET", "LIV_BFF_SERVICE_CLIENT_SECRET", "LIV_AUDIT_IMPORTER_SECRET");
  private static final String MFA_ROLE = "liv-mfa-requerido";
  private static final Set<String> MFA_ROLES =
      Set.of(
          "ADMIN_SISTEMA",
          "APROBADOR_TABLAS",
          "APROBADOR_LIQUIDACION",
          "APROBADOR_RECAUDO",
          "TESORERIA",
          "JURIDICO",
          "AUDITOR");
  private static final Set<String> ROLES =
      Set.of(
          "ADMIN_SISTEMA",
          "CARGADOR_TABLAS",
          "APROBADOR_TABLAS",
          "LIQUIDADOR",
          "APROBADOR_LIQUIDACION",
          "RECAUDO",
          "TESORERIA",
          "APROBADOR_RECAUDO",
          "JURIDICO",
          "CONSULTA",
          "AUDITOR");

  private static String baseText;
  private static Map<String, Object> realm;
  private static Map<String, Object> dev;

  @BeforeAll
  static void load() throws IOException {
    baseText = Files.readString(BASE, StandardCharsets.UTF_8);
    realm = new Yaml().load(resolveWithProductionValues(baseText));
    dev = new Yaml().load(Files.readString(DEV, StandardCharsets.UTF_8));
  }

  /** Reemplaza $(env:VAR:-valor) por su valor de producción; los secretos no tienen uno. */
  private static String resolveWithProductionValues(String text) {
    Matcher m = VAR.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String name = m.group(1);
      String fallback = m.group(2);
      if (SECRETS.contains(name)) {
        assertThat(fallback).as("el secreto %s no debe tener valor por defecto", name).isNull();
        fallback = "secreto-de-prueba";
      }
      assertThat(fallback).as("la variable %s necesita su valor de producción", name).isNotNull();
      m.appendReplacement(out, Matcher.quoteReplacement(fallback));
    }
    m.appendTail(out);
    return out.toString();
  }

  @Test
  @DisplayName("Realm liv en español, sin registro, recuperación ni consola de cuenta")
  void realmBasics() {
    assertThat(realm).containsEntry("realm", "liv").containsEntry("enabled", true);
    assertThat(realm)
        .containsEntry("defaultLocale", "es")
        .containsEntry("registrationAllowed", false);
    assertThat(realm)
        .containsEntry("resetPasswordAllowed", false)
        .containsEntry("rememberMe", false);
    assertThat(client("account")).containsEntry("enabled", false);
    assertThat(client("account-console")).containsEntry("enabled", false);
  }

  @Test
  @DisplayName("001-06 · Los 7 roles con segundo factor obligatorio llevan el rol de MFA")
  void roles() {
    Map<String, Map<String, Object>> byName =
        list(map(realm.get("roles")).get("realm")).stream()
            .collect(Collectors.toMap(r -> (String) r.get("name"), r -> r));
    assertThat(byName.keySet()).containsAll(ROLES).contains(MFA_ROLE);
    for (String role : ROLES) {
      boolean composite = byName.get(role).containsKey("composites");
      assertThat(composite).as("composición de %s", role).isEqualTo(MFA_ROLES.contains(role));
      if (composite) {
        assertThat(strings(map(byName.get(role).get("composites")).get("realm")))
            .containsExactly(MFA_ROLE);
      }
    }
  }

  @Test
  @DisplayName("001-06 · El flujo de navegador exige TOTP a quien tiene el rol de MFA")
  void conditionalOtpFlow() {
    assertThat(realm).containsEntry("browserFlow", "liv-browser");
    String config =
        list(realm.get("authenticatorConfig")).stream()
            .filter(c -> "liv-mfa-por-rol".equals(c.get("alias")))
            .map(c -> (String) map(c.get("config")).get("condUserRole"))
            .findFirst()
            .orElseThrow();
    assertThat(config).isEqualTo(MFA_ROLE);
    List<Map<String, Object>> otp = executions("liv-browser-mfa");
    assertThat(otp)
        .anySatisfy(e -> assertThat(e).containsEntry("authenticator", "conditional-user-role"))
        .anySatisfy(e ->
            assertThat(e)
                .containsEntry("authenticator", "auth-otp-form")
                .containsEntry("requirement", "REQUIRED"));
    assertThat(list(realm.get("requiredActions")))
        .anySatisfy(a ->
            assertThat(a).containsEntry("alias", "CONFIGURE_TOTP").containsEntry("enabled", true));
    assertThat(realm).containsEntry("otpPolicyDigits", 6).containsEntry("otpPolicyPeriod", 30);
  }

  @Test
  @DisplayName("001-04 · Bloqueo de 15 minutos tras 5 intentos fallidos")
  void bruteForce() {
    assertThat(realm)
        .containsEntry("bruteForceProtected", true)
        .containsEntry("permanentLockout", false)
        .containsEntry("failureFactor", 5)
        .containsEntry("waitIncrementSeconds", 900)
        .containsEntry("maxFailureWaitSeconds", 900);
  }

  @Test
  @DisplayName("D-001-7 · Política de contraseñas")
  void passwordPolicy() {
    String policy = (String) realm.get("passwordPolicy");
    assertThat(policy).contains("length(12)", "passwordHistory(5)", "notUsername");
    assertThat(policy).doesNotContain("forceExpiredPasswordChange");
  }

  @Test
  @DisplayName(
      "001-10 · Tiempos: token de 5 min, inactividad del proveedor mayor que la del BFF y máximo de 8 h")
  void sessionTimes() {
    int bffIdle = 15 * 60;
    int accessToken = (int) realm.get("accessTokenLifespan");
    assertThat(accessToken).isEqualTo(300);
    assertThat((int) realm.get("ssoSessionIdleTimeout"))
        .isGreaterThanOrEqualTo(bffIdle + accessToken + 300);
    assertThat((int) realm.get("ssoSessionMaxLifespan")).isEqualTo(8 * 3600 + 600);
    assertThat(realm).containsEntry("revokeRefreshToken", true);
  }

  @Test
  @DisplayName("001-20 · Cliente liv-bff: OIDC estándar, PKCE, claims planos y sin offline_access")
  void bffClient() {
    Map<String, Object> bff = client("liv-bff");
    assertThat(bff)
        .containsEntry("publicClient", false)
        .containsEntry("standardFlowEnabled", true)
        .containsEntry("directAccessGrantsEnabled", false)
        .containsEntry("serviceAccountsEnabled", false);
    assertThat(map(bff.get("attributes")))
        .containsEntry("pkce.code.challenge.method", "S256")
        .containsKey("backchannel.logout.url");
    assertThat(strings(bff.get("defaultClientScopes"))).doesNotContain("roles", "offline_access");
    assertThat(strings(bff.getOrDefault("optionalClientScopes", List.of())))
        .doesNotContain("offline_access");
    assertThat(mapperValues(bff, "claim.name")).contains("roles", "administration_id");
    assertThat(mapperValues(bff, "included.custom.audience")).containsExactly("liv-api");
  }

  @Test
  @DisplayName(
      "Cliente liv-bff-service: solo client credentials y audiencia exclusiva liv-internal")
  void bffServiceClient() {
    Map<String, Object> service = client("liv-bff-service");
    assertThat(service)
        .containsEntry("standardFlowEnabled", false)
        .containsEntry("directAccessGrantsEnabled", false)
        .containsEntry("serviceAccountsEnabled", true);
    assertThat(mapperValues(service, "included.custom.audience")).containsExactly("liv-internal");
  }

  @Test
  @DisplayName("001-18 · Cliente liv-audit-importer con solo lectura de eventos y usuarios")
  void auditImporterClient() {
    assertThat(client("liv-audit-importer"))
        .containsEntry("standardFlowEnabled", false)
        .containsEntry("serviceAccountsEnabled", true);
    Map<String, Object> account =
        list(realm.get("users")).stream()
            .filter(u -> "service-account-liv-audit-importer".equals(u.get("username")))
            .findFirst()
            .orElseThrow();
    assertThat(strings(map(account.get("clientRoles")).get("realm-management")))
        .containsExactlyInAnyOrder("view-events", "view-users");
  }

  @Test
  @DisplayName("001-18 · Eventos de usuario y de administración con detalle y caducidad de 30 días")
  void events() {
    assertThat(realm)
        .containsEntry("eventsEnabled", true)
        .containsEntry("eventsExpiration", 2592000)
        .containsEntry("adminEventsEnabled", true)
        .containsEntry("adminEventsDetailsEnabled", true);
  }

  @Test
  @DisplayName("001-17 · Solo un administrador ve y edita administration_id")
  void administrationAttribute() {
    String profile =
        (String)
            list(map(realm.get("components")).get("org.keycloak.userprofile.UserProfileProvider"))
                .stream()
                .map(c ->
                    list(map(c.get("config")).get("kc.user.profile.config")).isEmpty() ? null : c)
                .map(c -> ((List<?>) map(c.get("config")).get("kc.user.profile.config")).get(0))
                .findFirst()
                .orElseThrow();
    Map<String, Object> attribute =
        list(map(new Yaml().load(profile)).get("attributes")).stream()
            .filter(a -> "administration_id".equals(a.get("name")))
            .findFirst()
            .orElseThrow();
    Map<String, Object> permissions = map(attribute.get("permissions"));
    assertThat(strings(permissions.get("view"))).containsExactly("admin");
    assertThat(strings(permissions.get("edit"))).containsExactly("admin");
  }

  @Test
  @DisplayName(
      "Capa de desarrollo: un usuario por rol, administraciones de la semilla y sin secretos en la base")
  void devLayer() {
    List<Map<String, Object>> users = list(dev.get("users"));
    Set<String> rolesWithUser =
        users.stream()
            .flatMap(u -> strings(u.getOrDefault("realmRoles", List.of())).stream())
            .collect(Collectors.toSet());
    assertThat(rolesWithUser).containsAll(ROLES);
    Set<String> administrations =
        users.stream()
            .map(u -> map(u.getOrDefault("attributes", Map.of())).get("administration_id"))
            .filter(v -> v != null)
            .flatMap(v -> strings(v).stream())
            .collect(Collectors.toSet());
    assertThat(administrations)
        .containsExactlyInAnyOrder(
            "10000000-0000-0000-0000-000000052356", "10000000-0000-0000-0000-0000000b0001");
    for (Map<String, Object> user : users) {
      assertThat(baseText).doesNotContain((String) user.get("username"));
      for (Map<String, Object> credential : list(user.getOrDefault("credentials", List.of()))) {
        Object value = credential.getOrDefault("value", credential.get("secretData"));
        assertThat(baseText).doesNotContain(String.valueOf(value));
      }
    }
  }

  private static Map<String, Object> client(String clientId) {
    return list(realm.get("clients")).stream()
        .filter(c -> clientId.equals(c.get("clientId")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("falta el cliente " + clientId));
  }

  private static List<Map<String, Object>> executions(String flowAlias) {
    return list(
        list(realm.get("authenticationFlows")).stream()
            .filter(f -> flowAlias.equals(f.get("alias")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("falta el flujo " + flowAlias))
            .get("authenticationExecutions"));
  }

  private static List<String> mapperValues(Map<String, Object> client, String key) {
    return list(client.getOrDefault("protocolMappers", List.of())).stream()
        .map(m -> (String) map(m.get("config")).get(key))
        .filter(v -> v != null)
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    return (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<String> strings(Object value) {
    return value instanceof List ? (List<String>) value : List.of(String.valueOf(value));
  }
}
