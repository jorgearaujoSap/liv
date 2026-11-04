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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Verifica la configuración del realm de Keycloak como código (tarea T07 de la feature 001). Lee
 * los archivos y resuelve sus variables con los valores de producción, como lo hará
 * keycloak-config-cli. Las rutas son relativas a backend/, el directorio desde el que Maven corre
 * las pruebas.
 */
@DisplayName("Realm liv de Keycloak (001 T07)")
class RealmConfigTest {

  /** Lo que se publica para todos los entornos (ADR 0024). */
  private static final Path CONFIG_DIR = Path.of("identity/config");

  private static final Path BASE = CONFIG_DIR.resolve("base/liv-realm.yaml");

  /** Capa solo de desarrollo: vive fuera de identity/config para que ninguna imagen la copie. */
  private static final Path DEV = Path.of("dev/keycloak/liv-realm-dev.yaml");

  /** The registry of LIV users: {@code core.admin_user}. */
  private static final Path REGISTRY_MIGRATION =
      Path.of("src/main/resources/db/migration/V202611010905__core_administrations_users.sql");

  private static final Pattern VAR = Pattern.compile("\\$\\(env:([A-Z0-9_]+)(?::-([^)]*))?\\)");

  /** Valores que da cada entorno (secretos y URL); no tienen valor por defecto. */
  private static final Set<String> ENVIRONMENT_VALUES =
      Set.of(
          "LIV_BFF_CLIENT_SECRET",
          "LIV_BFF_SERVICE_CLIENT_SECRET",
          "LIV_AUDIT_IMPORTER_SECRET",
          "LIV_BFF_BASE_URL",
          "LIV_BFF_BACKCHANNEL_URL");

  // Tabla "Sesión y tiempos" del plan de la feature 001.
  private static final int BFF_IDLE_SECONDS = 15 * 60;
  private static final int ACCESS_TOKEN_SECONDS = 5 * 60;
  private static final int IDLE_MARGIN_SECONDS = 5 * 60;
  private static final int MAX_SESSION_SECONDS = 8 * 3600 + 10 * 60;

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
  private static final Set<String> DEV_USER_KEYS =
      Set.of(
          "username",
          "enabled",
          "email",
          "firstName",
          "lastName",
          "attributes",
          "realmRoles",
          "credentials");
  private static final Set<String> LIV_CLIENTS =
      Set.of("liv-bff", "liv-bff-service", "liv-audit-importer");

  private static String baseText;
  private static Map<String, Object> rawRealm;
  private static Map<String, Object> realm;
  private static Map<String, Object> dev;

  @BeforeAll
  static void load() throws IOException {
    baseText = Files.readString(BASE, StandardCharsets.UTF_8);
    rawRealm = new Yaml().load(baseText);
    realm = new Yaml().load(resolveWithProductionValues(baseText));
    dev = new Yaml().load(Files.readString(DEV, StandardCharsets.UTF_8));
  }

  /** Reemplaza $(env:VAR:-valor) por su valor de producción, como keycloak-config-cli. */
  private static String resolveWithProductionValues(String text) {
    Matcher m = VAR.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String name = m.group(1);
      String fallback = m.group(2);
      if (ENVIRONMENT_VALUES.contains(name)) {
        assertThat(fallback)
            .as("%s depende del entorno y no debe tener valor por defecto", name)
            .isNull();
        fallback = "valor-de-prueba";
      }
      assertThat(fallback).as("la variable %s necesita su valor de producción", name).isNotNull();
      m.appendReplacement(out, Matcher.quoteReplacement(fallback));
    }
    m.appendTail(out);
    return out.toString();
  }

  @Test
  @DisplayName("ADR 0024 · Realm liv en español, solo HTTPS, sin registro ni recuperación")
  void realmBasics() {
    assertThat(realm)
        .containsEntry("realm", "liv")
        .containsEntry("enabled", true)
        .containsEntry("sslRequired", "all")
        .containsEntry("defaultLocale", "es")
        .containsEntry("registrationAllowed", false)
        .containsEntry("resetPasswordAllowed", false)
        .containsEntry("rememberMe", false);
  }

  @Test
  @DisplayName("Enmienda 1 · Consola de cuenta desactivada")
  void accountConsoleDisabled() {
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
    // Cada flujo con solo sus ejecuciones: otra alternativa permitiría entrar sin TOTP.
    for (String flow : List.of("liv-browser", "liv-browser-forms", "liv-browser-mfa")) {
      assertThat(flow(flow))
          .as(flow)
          .containsEntry("providerId", "basic-flow")
          .containsEntry("topLevel", "liv-browser".equals(flow));
      assertThat(executions(flow)).as(flow).hasSize(2);
    }
    assertThat(execution("liv-browser", "authenticator", "auth-cookie"))
        .containsEntry("requirement", "ALTERNATIVE");
    assertThat(execution("liv-browser", "flowAlias", "liv-browser-forms"))
        .containsEntry("requirement", "ALTERNATIVE");
    Map<String, Object> password =
        execution("liv-browser-forms", "authenticator", "auth-username-password-form");
    Map<String, Object> mfa = execution("liv-browser-forms", "flowAlias", "liv-browser-mfa");
    assertThat(password).containsEntry("requirement", "REQUIRED");
    assertThat(mfa).containsEntry("requirement", "CONDITIONAL");
    assertThat(priority(password)).isLessThan(priority(mfa));

    // Primero la condición por rol y luego el TOTP.
    Map<String, Object> condition =
        execution("liv-browser-mfa", "authenticator", "conditional-user-role");
    Map<String, Object> otp = execution("liv-browser-mfa", "authenticator", "auth-otp-form");
    assertThat(condition)
        .containsEntry("requirement", "REQUIRED")
        .containsEntry("authenticatorConfig", "liv-mfa-por-rol");
    assertThat(otp).containsEntry("requirement", "REQUIRED");
    assertThat(priority(condition)).isLessThan(priority(otp));
    Map<String, Object> config =
        map(
            list(realm.get("authenticatorConfig")).stream()
                .filter(c -> "liv-mfa-por-rol".equals(c.get("alias")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta la configuración liv-mfa-por-rol"))
                .get("config"));
    assertThat(config).containsEntry("condUserRole", MFA_ROLE).containsEntry("negate", "false");

    assertThat(list(realm.get("requiredActions")))
        .anySatisfy(a ->
            assertThat(a).containsEntry("alias", "CONFIGURE_TOTP").containsEntry("enabled", true));
    assertThat(realm).containsEntry("otpPolicyDigits", 6).containsEntry("otpPolicyPeriod", 30);
  }

  @Test
  @DisplayName("001-06 · Nadie entra con usuario y contraseña por API, sin el flujo de navegador")
  void directGrantDenied() {
    assertThat(realm).containsEntry("directGrantFlow", "liv-direct-grant-denegado");
    assertThat(executions("liv-direct-grant-denegado"))
        .singleElement()
        .satisfies(e ->
            assertThat(e)
                .containsEntry("authenticator", "deny-access-authenticator")
                .containsEntry("requirement", "REQUIRED"));
    assertThat(client("admin-cli")).containsEntry("enabled", false);
  }

  @Test
  @DisplayName(
      "001-04 · Bloqueo de 15 minutos tras 5 intentos fallidos, fijo en todos los entornos")
  void bruteForce() {
    // Valores literales (rawRealm): ningún entorno puede debilitarlos con una variable.
    assertThat(rawRealm)
        .containsEntry("bruteForceProtected", true)
        .containsEntry("permanentLockout", false)
        .containsEntry("failureFactor", 5)
        .containsEntry("waitIncrementSeconds", 900)
        .containsEntry("maxFailureWaitSeconds", 900)
        .containsEntry("minimumQuickLoginWaitSeconds", 60)
        .containsEntry("quickLoginCheckMilliSeconds", 1000);
    assertThat(rawRealm.get("sslRequired")).isEqualTo("all");
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
    int accessToken = (int) realm.get("accessTokenLifespan");
    assertThat(accessToken).isEqualTo(ACCESS_TOKEN_SECONDS);
    assertThat((int) realm.get("ssoSessionIdleTimeout"))
        .isGreaterThanOrEqualTo(BFF_IDLE_SECONDS + accessToken + IDLE_MARGIN_SECONDS);
    assertThat((int) realm.get("ssoSessionMaxLifespan")).isEqualTo(MAX_SESSION_SECONDS);
    // Sin rotación del refresh token: dos renovaciones simultáneas del BFF cerrarían la sesión.
    assertThat(realm).containsEntry("revokeRefreshToken", false);
  }

  @Test
  @DisplayName("001-20 · El rol por defecto del realm no da offline_access ni otros roles")
  void defaultRoleWithoutComposites() {
    Map<String, Object> defaultRole =
        list(map(realm.get("roles")).get("realm")).stream()
            .filter(r -> "default-roles-liv".equals(r.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("falta el rol por defecto default-roles-liv"));
    // keycloak-config-cli conserva los compuestos que Keycloak crea si la clave no se declara.
    assertThat(defaultRole).containsKey("composites");
    Map<String, Object> composites = map(defaultRole.get("composites"));
    assertThat(composites).containsKeys("realm", "client");
    assertThat(strings(composites.get("realm"))).isEmpty();
    assertThat(map(composites.get("client"))).isEmpty();
  }

  @Test
  @DisplayName("001-20 · Clientes de LIV confidenciales, sin flujo implícito ni contraseña directa")
  void livClientsHardened() {
    for (Map<String, Object> declared : list(realm.get("clients"))) {
      assertThat(declared)
          .as((String) declared.get("clientId"))
          .doesNotContainKey("authenticationFlowBindingOverrides");
    }
    for (String clientId : LIV_CLIENTS) {
      assertThat(client(clientId))
          .as(clientId)
          .containsEntry("enabled", true)
          .containsEntry("publicClient", false)
          .containsEntry("implicitFlowEnabled", false)
          .containsEntry("directAccessGrantsEnabled", false);
      // El secreto viene del entorno: nunca un literal en la base.
      assertThat((String) rawClient(clientId).get("secret"))
          .as("secreto de %s", clientId)
          .matches("\\$\\(env:[A-Z_]+_SECRET\\)");
    }
  }

  @Test
  @DisplayName("001-20 · Cliente liv-bff: OIDC estándar, PKCE, claims planos y sin offline_access")
  void bffClient() {
    Map<String, Object> bff = client("liv-bff");
    assertThat(bff)
        .containsEntry("standardFlowEnabled", true)
        .containsEntry("serviceAccountsEnabled", false)
        .containsEntry("consentRequired", false);
    assertThat(strings(bff.get("webOrigins"))).isEmpty();
    assertThat(strings(rawClient("liv-bff").get("redirectUris")))
        .containsExactly("$(env:LIV_BFF_BASE_URL)/login/oauth2/code/liv");
    assertThat(map(bff.get("attributes"))).containsEntry("pkce.code.challenge.method", "S256");
    // El cierre por canal trasero va por la red interna, no por la URL pública del BFF.
    assertThat(map(rawClient("liv-bff").get("attributes")))
        .containsEntry(
            "backchannel.logout.url", "$(env:LIV_BFF_BACKCHANNEL_URL)/bff/backchannel-logout");
    assertThat(strings(bff.get("defaultClientScopes"))).doesNotContain("roles", "offline_access");
    assertThat(strings(bff.getOrDefault("optionalClientScopes", List.of())))
        .doesNotContain("offline_access");
    assertThat(mapperValues(bff, "claim.name")).contains("roles", "administration_id");
    assertThat(mapperValues(bff, "included.custom.audience")).containsExactly("liv-api");
  }

  @Test
  @DisplayName(
      "ADR 0025 · Cliente liv-bff-service: solo client credentials y audiencia exclusiva liv-internal")
  void bffServiceClient() {
    Map<String, Object> service = client("liv-bff-service");
    assertThat(service)
        .containsEntry("standardFlowEnabled", false)
        .containsEntry("serviceAccountsEnabled", true);
    assertThat(mapperValues(service, "included.custom.audience")).containsExactly("liv-internal");
  }

  @Test
  @DisplayName("001-18 · Cliente liv-audit-importer con solo lectura de eventos y usuarios")
  void auditImporterClient() {
    assertThat(client("liv-audit-importer"))
        .containsEntry("standardFlowEnabled", false)
        .containsEntry("serviceAccountsEnabled", true);
    Map<String, Object> serviceAccount =
        list(realm.get("users")).stream()
            .filter(u -> "service-account-liv-audit-importer".equals(u.get("username")))
            .findFirst()
            .orElseThrow();
    assertThat(strings(map(serviceAccount.get("clientRoles")).get("realm-management")))
        .containsExactlyInAnyOrder("view-events", "view-users");
  }

  @Test
  @DisplayName("001-18 · Eventos guardados con detalle 30 días y sin escribirlos en el log")
  void events() {
    assertThat(realm)
        .containsEntry("eventsEnabled", true)
        .containsEntry("eventsExpiration", 2592000)
        .containsEntry("adminEventsEnabled", true)
        .containsEntry("adminEventsDetailsEnabled", true);
    // jboss-logging escribiría el usuario intentado y la IP sin enmascarar (doc. 02 §6).
    assertThat(strings(realm.get("eventsListeners"))).isEmpty();
  }

  @Test
  @DisplayName(
      "001-21 · El nombre y el correo del perfil caben en el registro de LIV (plan, ajustes de T13)")
  void profileFitsTheLivRegistry() throws IOException {
    // The lengths of core.admin_user, read from its migration: the registry is the authority.
    String migration =
        Files.readString(REGISTRY_MIGRATION, StandardCharsets.UTF_8).replace("\r", "");
    int fullName = columnLength(migration, "full_name");
    int email = columnLength(migration, "email");

    // The name claim is "firstName lastName": both, with the space, fit in full_name.
    assertThat(maxLength("firstName") + 1 + maxLength("lastName")).isLessThanOrEqualTo(fullName);
    assertThat(maxLength("email")).isLessThanOrEqualTo(email);
  }

  @Test
  @DisplayName("001-17 · Solo un administrador ve y edita administration_id")
  void administrationAttribute() {
    Map<String, Object> attribute =
        list(userProfile().get("attributes")).stream()
            .filter(a -> "administration_id".equals(a.get("name")))
            .findFirst()
            .orElseThrow();
    Map<String, Object> permissions = map(attribute.get("permissions"));
    assertThat(strings(permissions.get("view"))).containsExactly("admin");
    assertThat(strings(permissions.get("edit"))).containsExactly("admin");
  }

  @Test
  @DisplayName("ADR 0024 · Lo que se publica en identity/config es solo la configuración base")
  void publishedConfigIsOnlyBase() throws IOException {
    // Cualquier imagen o despliegue que copie identity/config no puede llevarse usuarios de prueba.
    try (Stream<Path> files = Files.walk(CONFIG_DIR)) {
      assertThat(files.filter(Files::isRegularFile).map(p -> CONFIG_DIR.relativize(p).toString()))
          .containsExactly(Path.of("base", "liv-realm.yaml").toString());
    }
  }

  @Test
  @DisplayName(
      "ADR 0024 · Capa de desarrollo: solo usuarios con contraseña, uno por rol, con su administración")
  void devLayer() {
    assertThat(dev.keySet()).containsExactlyInAnyOrder("realm", "users", "sslRequired");
    assertThat(dev).containsEntry("sslRequired", "external");
    List<Map<String, Object>> users = list(dev.get("users"));
    // Sin roles de clientes (como realm-management) ni otras claves que den permisos.
    assertThat(users)
        .allSatisfy(u ->
            assertThat(DEV_USER_KEYS)
                .as("claves de %s", u.get("username"))
                .containsAll(u.keySet()));
    Set<String> rolesWithUser =
        users.stream()
            .flatMap(u -> strings(u.getOrDefault("realmRoles", List.of())).stream())
            .collect(Collectors.toSet());
    assertThat(rolesWithUser).containsAll(ROLES);
    for (Map<String, Object> user : users) {
      Object administration =
          map(user.getOrDefault("attributes", Map.of())).get("administration_id");
      assertThat(administration).as("administration_id de %s", user.get("username")).isNotNull();
      assertThat(list(user.get("credentials")))
          .as("credenciales de %s", user.get("username"))
          .allSatisfy(c -> assertThat(c).containsEntry("type", "password"));
    }
    Set<String> administrations =
        users.stream()
            .flatMap(u -> strings(map(u.get("attributes")).get("administration_id")).stream())
            .collect(Collectors.toSet());
    assertThat(administrations)
        .containsExactlyInAnyOrder(
            "10000000-0000-0000-0000-000000052356", "10000000-0000-0000-0000-0000000b0001");
  }

  private static Map<String, Object> client(String clientId) {
    return findClient(realm, clientId);
  }

  private static Map<String, Object> rawClient(String clientId) {
    return findClient(rawRealm, clientId);
  }

  private static Map<String, Object> findClient(Map<String, Object> source, String clientId) {
    return list(source.get("clients")).stream()
        .filter(c -> clientId.equals(c.get("clientId")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("falta el cliente " + clientId));
  }

  private static Map<String, Object> flow(String flowAlias) {
    return list(realm.get("authenticationFlows")).stream()
        .filter(f -> flowAlias.equals(f.get("alias")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("falta el flujo " + flowAlias));
  }

  private static List<Map<String, Object>> executions(String flowAlias) {
    return list(flow(flowAlias).get("authenticationExecutions"));
  }

  private static Map<String, Object> execution(String flowAlias, String key, String value) {
    return executions(flowAlias).stream()
        .filter(e -> value.equals(e.get(key)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("falta " + value + " en el flujo " + flowAlias));
  }

  private static int priority(Map<String, Object> execution) {
    return (int) execution.get("priority");
  }

  /** The maximum length the profile allows for {@code attribute}. */
  private static int maxLength(String attribute) {
    Map<String, Object> declared =
        list(userProfile().get("attributes")).stream()
            .filter(a -> attribute.equals(a.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("falta " + attribute + " en el perfil"));
    return (int) map(map(declared.get("validations")).get("length")).get("max");
  }

  private static int columnLength(String migration, String column) {
    Matcher declared =
        Pattern.compile("\\n\\s*" + column + "\\s+VARCHAR\\((\\d+)\\)").matcher(migration);
    if (!declared.find()) {
      throw new AssertionError("falta la columna " + column + " en " + REGISTRY_MIGRATION);
    }
    return Integer.parseInt(declared.group(1));
  }

  /** El perfil de usuario declarativo viaja como un JSON dentro de una cadena del componente. */
  private static Map<String, Object> userProfile() {
    Map<String, Object> provider =
        list(map(realm.get("components")).get("org.keycloak.userprofile.UserProfileProvider"))
            .get(0);
    String json = strings(map(provider.get("config")).get("kc.user.profile.config")).get(0);
    return new Yaml().load(json);
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
