package co.com.sapenlinea.liv.bff.support;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A generic OIDC provider in a container ({@code mock-oauth2-server}, ADR 0023), not Keycloak: the
 * BFF talks standard OpenID Connect to it (001-20). Its login form takes any user name; the tokens
 * it issues carry the claims below. The id token also lists the client as audience, as the OIDC
 * client validates.
 */
public final class OidcProvider {

  public static final String SUBJECT = "usuario-1";
  public static final String SESSION_ID = "sid-1";
  public static final String USER_CLIENT = "liv-bff";
  public static final String SERVICE_CLIENT = "liv-bff-service";

  /** The header of an unsigned JWT ({"alg":"none"}): how this provider issues its refresh token. */
  public static final String UNSIGNED_JWT_HEADER =
      java.util.Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

  private static final String ISSUER_ID = "liv";
  private static final String ISSUER_WITHOUT_SID = "liv-sin-sid";
  private static final String IMAGE =
      "ghcr.io/navikt/mock-oauth2-server:3.0.1@sha256:4f3660e5f3586b81fe2e0d42d0c9fcd3c8c64268db0d3ade261dadd70d27020b";
  private static final int PORT = 8080;

  private static final GenericContainer<?> SERVER = start();

  private OidcProvider() {}

  public static String issuer() {
    return "http://%s:%d/%s".formatted(SERVER.getHost(), SERVER.getMappedPort(PORT), ISSUER_ID);
  }

  /** An issuer whose tokens carry no {@code sid} claim. */
  public static String issuerWithoutSid() {
    return "http://%s:%d/%s"
        .formatted(SERVER.getHost(), SERVER.getMappedPort(PORT), ISSUER_WITHOUT_SID);
  }

  private static GenericContainer<?> start() {
    Map<String, Object> user =
        Map.of(
            "requestParam",
            "grant_type",
            "match",
            "authorization_code",
            "claims",
            Map.of(
                "sub",
                SUBJECT,
                "sid",
                SESSION_ID,
                "name",
                "Usuario de prueba",
                "email",
                "usuario-1@test.invalid",
                "azp",
                USER_CLIENT,
                "aud",
                List.of("liv-api", USER_CLIENT)));
    // A second issuer on the same server, for the same user without the sid claim: a provider that
    // does not send the session id (see MissingSessionIdTest).
    Map<String, Object> withoutSid =
        Map.of(
            "requestParam",
            "grant_type",
            "match",
            "authorization_code",
            "claims",
            Map.of(
                "sub",
                SUBJECT,
                "name",
                "Usuario de prueba",
                "email",
                "usuario-1@test.invalid",
                "azp",
                USER_CLIENT,
                "aud",
                List.of("liv-api", USER_CLIENT)));
    Map<String, Object> service =
        Map.of(
            "requestParam",
            "grant_type",
            "match",
            "client_credentials",
            "claims",
            Map.of(
                "sub",
                "service-account-" + SERVICE_CLIENT,
                "azp",
                SERVICE_CLIENT,
                "aud",
                List.of("liv-internal")));
    String config =
        JSONObjectUtils.toJSONString(
            Map.of(
                "interactiveLogin",
                false,
                "tokenCallbacks",
                List.of(
                    Map.of(
                        "issuerId",
                        ISSUER_ID,
                        "tokenExpiry",
                        300,
                        "requestMappings",
                        List.of(user, service)),
                    Map.of(
                        "issuerId",
                        ISSUER_WITHOUT_SID,
                        "tokenExpiry",
                        300,
                        "requestMappings",
                        List.of(withoutSid, service)))));
    GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withEnv("JSON_CONFIG", config)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forHttp("/" + ISSUER_ID + "/.well-known/openid-configuration"));
    container.start();
    return container;
  }
}
