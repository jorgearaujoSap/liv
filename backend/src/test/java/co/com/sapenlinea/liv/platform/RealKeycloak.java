package co.com.sapenlinea.liv.platform;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
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
 * by keycloak-config-cli exactly as {@code dev/compose.yaml} does. It hands out real tokens: the
 * service token of the BFF (client credentials) and a user token through the authorization code
 * flow with PKCE, with the fictitious users and secrets of the development layer.
 */
final class RealKeycloak {

  static final String USER = "dev.consulta";
  static final String ADMINISTRATION = "10000000-0000-0000-0000-000000052356";

  private static final String KEYCLOAK_IMAGE =
      "quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332";
  private static final String CONFIG_CLI_IMAGE =
      "adorsys/keycloak-config-cli:6.5.1-26.5.5@sha256:0955d98c8a341898b7aa177477edf8a1e90569ae50bbe7598141c1270b773274";
  private static final int PORT = 8080;

  /** Fictitious values of a test environment, like those of {@code dev/.env.example}. */
  private static final String ADMIN = "admin-prueba";

  private static final String ADMIN_PASSWORD = "admin-prueba-pw";
  private static final String PASSWORD = "Liv-Dev-2026!";
  private static final String BFF_SECRET = "liv-bff-prueba";
  private static final String SERVICE_SECRET = "liv-bff-service-prueba";
  private static final String BFF_BASE_URL = "http://localhost:4200";
  private static final String REDIRECT_URI = BFF_BASE_URL + "/login/oauth2/code/liv";

  private static final Pattern FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");
  private static final Pattern CODE = Pattern.compile("[?&]code=([^&]+)");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final GenericContainer<?> KEYCLOAK = start();

  private RealKeycloak() {}

  static String issuer() {
    return "http://%s:%d/realms/liv".formatted(KEYCLOAK.getHost(), KEYCLOAK.getMappedPort(PORT));
  }

  /** The client credentials token of {@code liv-bff-service}. */
  static String serviceToken() {
    return token(
        Map.of(
            "grant_type", "client_credentials",
            "client_id", "liv-bff-service",
            "client_secret", SERVICE_SECRET));
  }

  /** A user token of {@link #USER}, through the browser flow the BFF uses (code and PKCE). */
  static String userToken() {
    // Without a cookie handler: Keycloak marks its session cookies Secure and the JDK would not
    // send
    // them back over http, so they are carried by hand, like a browser on localhost does.
    HttpClient browser =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    String verifier = randomVerifier();
    HttpResponse<String> loginPage =
        send(
            browser,
            HttpRequest.newBuilder(
                    URI.create(
                        issuer()
                            + "/protocol/openid-connect/auth?"
                            + form(
                                Map.of(
                                    "client_id", "liv-bff",
                                    "response_type", "code",
                                    "scope", "openid",
                                    "redirect_uri", REDIRECT_URI,
                                    "state", "estado-prueba",
                                    "code_challenge", challenge(verifier),
                                    "code_challenge_method", "S256"))))
                .GET());
    String login = loginPage.body();
    String cookies =
        loginPage.headers().allValues("Set-Cookie").stream()
            .map(cookie -> cookie.split(";", 2)[0])
            .collect(Collectors.joining("; "));
    Matcher action = FORM_ACTION.matcher(login);
    if (!action.find()) {
      throw new IllegalStateException("Keycloak no mostró el formulario de inicio de sesión");
    }
    HttpResponse<String> afterLogin =
        send(
            browser,
            HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookies)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        form(Map.of("username", USER, "password", PASSWORD)))));
    String location = afterLogin.headers().firstValue("Location").orElse("");
    Matcher code = CODE.matcher(location);
    if (!location.startsWith(REDIRECT_URI) || !code.find()) {
      throw new IllegalStateException(
          "Keycloak no devolvió un código (estado " + afterLogin.statusCode() + ")");
    }
    return token(
        Map.of(
            "grant_type",
            "authorization_code",
            "client_id",
            "liv-bff",
            "client_secret",
            BFF_SECRET,
            "code",
            code.group(1),
            "redirect_uri",
            REDIRECT_URI,
            "code_verifier",
            verifier));
  }

  private static String token(Map<String, String> parameters) {
    HttpResponse<String> response =
        send(
            HttpClient.newHttpClient(),
            HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(parameters))));
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Keycloak no emitió el token (estado " + response.statusCode() + ")");
    }
    return JSON.readTree(response.body()).get("access_token").asString();
  }

  private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request) {
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
    Network network = Network.newNetwork();
    GenericContainer<?> keycloak =
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
    keycloak.start();
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
            .withEnv("LIV_AUDIT_IMPORTER_SECRET", "liv-audit-importer-prueba")
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("identity/config/base/liv-realm.yaml")),
                "/config/base/liv-realm.yaml")
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("dev/keycloak/liv-realm-dev.yaml")),
                "/config/dev/liv-realm-dev.yaml")
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(4)))) {
      config.start();
    }
    return keycloak;
  }
}
