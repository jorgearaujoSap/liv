package co.com.sapenlinea.liv.platform;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real identity provider of LIV (D-001-6, plan › ajustes de T12): Keycloak with the realm as
 * code of {@code identity/config/base} and the development layer of {@code dev/keycloak}, applied
 * by keycloak-config-cli with the images, files and variables of {@code dev/compose.yaml}. It hands
 * out real tokens: the service token of the BFF (client credentials) and user tokens through the
 * authorization code flow with PKCE, with the fictitious users and secrets of the development
 * layer. Clients authenticate with HTTP Basic, the default of Spring Security.
 *
 * <p>Started once per test run, on first use; if the realm cannot be applied, the containers are
 * stopped and the error says why.
 */
public final class RealKeycloak {

  /** Users of {@code dev/keycloak/liv-realm-dev.yaml} without a second factor (role CONSULTA). */
  public static final String USER = "dev.consulta";

  /** Secret of {@code liv-audit-importer} in this provider (T15). */
  public static final String AUDIT_IMPORTER_SECRET = "liv-audit-importer-prueba";

  static final String USER_OF_B = "dev.consulta.b";

  /** {@code administration_id} of {@link #USER} in the development layer (Ipiales). */
  static final String ADMINISTRATION = "10000000-0000-0000-0000-000000052356";

  /** {@code administration_id} of {@link #USER_OF_B} in the development layer. */
  static final String ADMINISTRATION_OF_B = "10000000-0000-0000-0000-0000000b0001";

  private static final String KEYCLOAK_IMAGE =
      "quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332";
  private static final String CONFIG_CLI_IMAGE =
      "adorsys/keycloak-config-cli:6.5.1-26.5.5@sha256:0955d98c8a341898b7aa177477edf8a1e90569ae50bbe7598141c1270b773274";
  private static final int PORT = 8080;
  private static final Path BASE_REALM = Path.of("identity/config/base/liv-realm.yaml");
  private static final Path DEV_REALM = Path.of("dev/keycloak/liv-realm-dev.yaml");

  /** Fictitious values of a test environment, like those of {@code dev/.env.example}. */
  private static final String ADMIN = "admin-prueba";

  private static final String ADMIN_PASSWORD = "admin-prueba-pw";
  private static final String PASSWORD = "Liv-Dev-2026!";
  private static final String BFF_SECRET = "liv-bff-prueba";
  private static final String SERVICE_SECRET = "liv-bff-service-prueba";
  private static final String BFF_BASE_URL = "http://localhost:4200";
  private static final String REDIRECT_URI = BFF_BASE_URL + "/login/oauth2/code/liv";

  private static final Pattern LOGIN_FORM =
      Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");
  private static final Pattern CODE = Pattern.compile("[?&]code=([^&]+)");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static GenericContainer<?> keycloak;

  private RealKeycloak() {}

  public static synchronized String issuer() {
    if (keycloak == null) {
      keycloak = start();
    }
    return "http://%s:%d/realms/liv".formatted(keycloak.getHost(), keycloak.getMappedPort(PORT));
  }

  /** The client credentials token of {@code liv-bff-service}. */
  static String serviceToken() {
    Response response =
        token("liv-bff-service", SERVICE_SECRET, Map.of("grant_type", "client_credentials"));
    if (response.status() != 200) {
      throw new IllegalStateException(
          "Keycloak no emitió el token de servicio (" + response.status() + ")");
    }
    return response.accessToken();
  }

  /** A user token through the browser flow the BFF uses: code with PKCE (S256). */
  static String userToken(String username) {
    String verifier = randomVerifier();
    Response response = exchange(login(username, challenge(verifier)), verifier);
    if (response.status() != 200) {
      throw new IllegalStateException("Keycloak no canjeó el código (" + response.status() + ")");
    }
    return response.accessToken();
  }

  /** The error body of exchanging a code with a verifier other than the challenged one. */
  static String exchangeWithAnotherVerifier(String username) {
    String code = login(username, challenge(randomVerifier()));
    return exchange(code, randomVerifier()).body();
  }

  /** Where the realm sends a browser that asks for a code without PKCE. */
  static String authorizeWithoutPkce() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("client_id", "liv-bff");
    query.put("response_type", "code");
    query.put("scope", "openid");
    query.put("redirect_uri", REDIRECT_URI);
    query.put("state", "estado-prueba");
    HttpResponse<String> response =
        send(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
            HttpRequest.newBuilder(
                    URI.create(issuer() + "/protocol/openid-connect/auth?" + form(query)))
                .GET());
    return response.headers().firstValue("Location").orElse(response.body());
  }

  /** A login with a wrong password through the login form, as a browser would (T15). */
  public static void failedLogin(String username) {
    HttpResponse<String> page =
        loginWith(username, "contrasena-equivocada", challenge(randomVerifier()));
    if (page.statusCode() != 200) {
      throw new IllegalStateException(
          "Keycloak no rechazó el inicio fallido (estado " + page.statusCode() + ")");
    }
  }

  /** Creates an enabled user of the realm with the administration API and returns its id (T15). */
  public static String createUser(String username) {
    HttpResponse<String> created =
        admin(
            HttpRequest.newBuilder(URI.create(adminBase() + "/users"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        ("{\"username\":\"%s\",\"enabled\":true,"
                                + "\"firstName\":\"Usuaria\",\"lastName\":\"De Prueba\"}")
                            .formatted(username))));
    String location = created.headers().firstValue("Location").orElseThrow();
    return location.substring(location.lastIndexOf('/') + 1);
  }

  /**
   * The status of creating a user with this JSON representation through the administration API, as
   * a console would, whatever it is: the profile of the realm may refuse it.
   */
  public static int createUserStatus(String representation) {
    return adminAnswer(
            HttpRequest.newBuilder(URI.create(adminBase() + "/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(representation)))
        .statusCode();
  }

  /** Grants a realm role to a user with the administration API, as an administrator would. */
  public static void grantRealmRole(String userId, String role) {
    String representation =
        admin(HttpRequest.newBuilder(URI.create(adminBase() + "/roles/" + role)).GET()).body();
    admin(
        HttpRequest.newBuilder(
                URI.create(adminBase() + "/users/" + userId + "/role-mappings/realm"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("[" + representation + "]")));
  }

  /** A call of the administration API of the realm as the bootstrap administrator. */
  private static HttpResponse<String> admin(HttpRequest.Builder request) {
    HttpResponse<String> response = adminAnswer(request);
    if (response.statusCode() >= 300) {
      throw new IllegalStateException(
          "La API de administración respondió " + response.statusCode() + ": " + response.body());
    }
    return response;
  }

  /** A call of the administration API of the realm as the bootstrap administrator, as answered. */
  private static HttpResponse<String> adminAnswer(HttpRequest.Builder request) {
    String root = issuer().substring(0, issuer().indexOf("/realms/"));
    HttpResponse<String> token =
        send(
            HttpClient.newHttpClient(),
            HttpRequest.newBuilder(
                    URI.create(root + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        form(
                            Map.of(
                                "grant_type",
                                "password",
                                "client_id",
                                "admin-cli",
                                "username",
                                ADMIN,
                                "password",
                                ADMIN_PASSWORD)))));
    String accessToken = JSON.readTree(token.body()).get("access_token").asString();
    return send(
        HttpClient.newHttpClient(), request.header("Authorization", "Bearer " + accessToken));
  }

  private static String adminBase() {
    return issuer().replace("/realms/", "/admin/realms/");
  }

  /** Logs in through the login form of the realm and returns the authorization code. */
  private static String login(String username, String codeChallenge) {
    HttpResponse<String> afterLogin = loginWith(username, PASSWORD, codeChallenge);
    String location = afterLogin.headers().firstValue("Location").orElse("");
    Matcher code = CODE.matcher(location);
    if (!location.startsWith(REDIRECT_URI) || !code.find()) {
      // A required action (password change, second factor) answers with a page, not a redirect.
      throw new IllegalStateException(
          "Keycloak no devolvió un código para "
              + username
              + " (estado "
              + afterLogin.statusCode()
              + "): ¿el usuario tiene una acción requerida?");
    }
    return code.group(1);
  }

  /** The answer of the login form to a username and password. */
  private static HttpResponse<String> loginWith(
      String username, String password, String codeChallenge) {
    // Keycloak's session cookies are carried by hand: the JDK cookie handler does not send back
    // cookies marked Secure over http, which a browser on localhost does.
    HttpClient browser =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    Map<String, String> query = new LinkedHashMap<>();
    query.put("client_id", "liv-bff");
    query.put("response_type", "code");
    query.put("scope", "openid");
    query.put("redirect_uri", REDIRECT_URI);
    query.put("state", "estado-prueba");
    query.put("code_challenge", codeChallenge);
    query.put("code_challenge_method", "S256");
    HttpResponse<String> page =
        send(
            browser,
            HttpRequest.newBuilder(
                    URI.create(issuer() + "/protocol/openid-connect/auth?" + form(query)))
                .GET());
    Matcher action = LOGIN_FORM.matcher(page.body());
    if (!action.find()) {
      throw new IllegalStateException(
          "Keycloak no mostró el formulario de inicio de sesión (estado "
              + page.statusCode()
              + ")");
    }
    return send(
        browser,
        HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", cookies(page))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    form(Map.of("username", username, "password", password)))));
  }

  private static Response exchange(String code, String verifier) {
    return token(
        "liv-bff",
        BFF_SECRET,
        Map.of(
            "grant_type", "authorization_code",
            "code", code,
            "redirect_uri", REDIRECT_URI,
            "code_verifier", verifier));
  }

  /** The token endpoint, with the client authenticated by HTTP Basic. */
  private static Response token(String clientId, String secret, Map<String, String> parameters) {
    String basic =
        Base64.getEncoder()
            .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    HttpResponse<String> response =
        send(
            HttpClient.newHttpClient(),
            HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(form(parameters))));
    if (response.statusCode() != 200) {
      return new Response(response.statusCode(), null, response.body());
    }
    return new Response(
        200, JSON.readTree(response.body()).get("access_token").asString(), response.body());
  }

  /** Name and value of each cookie the page sets, the last one by name, without deletions. */
  private static String cookies(HttpResponse<String> page) {
    Map<String, String> byName = new LinkedHashMap<>();
    for (String header : page.headers().allValues("Set-Cookie")) {
      String[] pair = header.split(";", 2)[0].split("=", 2);
      if (pair.length == 2 && !pair[1].isEmpty()) {
        byName.put(pair[0], pair[1]);
      }
    }
    return byName.entrySet().stream()
        .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
        .collect(Collectors.joining("; "));
  }

  static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request) {
    try {
      return client.send(
          request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static String form(Map<String, String> parameters) {
    return parameters.entrySet().stream()
        .map(entry ->
            URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private static String randomVerifier() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String challenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static GenericContainer<?> start() {
    for (Path realm : new Path[] {BASE_REALM, DEV_REALM}) {
      if (!Files.isRegularFile(realm)) {
        throw new IllegalStateException(
            "No se encontró " + realm + ": las pruebas del backend corren desde backend/");
      }
    }
    Network network = Network.newNetwork();
    GenericContainer<?> server =
        new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withNetwork(network)
            .withNetworkAliases("keycloak")
            .withCommand("start-dev")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(
                Wait.forHttp("/realms/master")
                    .forPort(PORT)
                    .withStartupTimeout(Duration.ofMinutes(4)));
    try {
      server.start();
      applyRealm(network);
      return server;
    } catch (RuntimeException e) {
      server.stop();
      network.close();
      throw new IllegalStateException("No se pudo levantar Keycloak con el realm de LIV", e);
    }
  }

  /** keycloak-config-cli as in {@code dev/compose.yaml}; it must end with exit code 0. */
  private static void applyRealm(Network network) {
    try (GenericContainer<?> config =
        new GenericContainer<>(DockerImageName.parse(CONFIG_CLI_IMAGE))
            .withNetwork(network)
            .withEnv("KEYCLOAK_URL", "http://keycloak:" + PORT)
            .withEnv("KEYCLOAK_USER", ADMIN)
            .withEnv("KEYCLOAK_PASSWORD", ADMIN_PASSWORD)
            .withEnv("KEYCLOAK_AVAILABILITYCHECK_ENABLED", "true")
            .withEnv("KEYCLOAK_AVAILABILITYCHECK_TIMEOUT", "120s")
            .withEnv(
                "IMPORT_FILES_LOCATIONS",
                "/config/base/liv-realm.yaml,/config/dev/liv-realm-dev.yaml")
            .withEnv("IMPORT_VARSUBSTITUTION_ENABLED", "true")
            .withEnv("LIV_BFF_BASE_URL", BFF_BASE_URL)
            .withEnv("LIV_BFF_BACKCHANNEL_URL", BFF_BASE_URL)
            .withEnv("LIV_BFF_CLIENT_SECRET", BFF_SECRET)
            .withEnv("LIV_BFF_SERVICE_CLIENT_SECRET", SERVICE_SECRET)
            .withEnv("LIV_AUDIT_IMPORTER_SECRET", AUDIT_IMPORTER_SECRET)
            .withCopyFileToContainer(
                MountableFile.forHostPath(BASE_REALM), "/config/base/liv-realm.yaml")
            .withCopyFileToContainer(
                MountableFile.forHostPath(DEV_REALM), "/config/dev/liv-realm-dev.yaml")
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(4)))) {
      config.start();
    }
  }

  private record Response(int status, String accessToken, String body) {}
}
