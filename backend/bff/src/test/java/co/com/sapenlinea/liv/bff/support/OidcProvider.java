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

  private static final String ISSUER_ID = "liv";
  private static final String IMAGE =
      "ghcr.io/navikt/mock-oauth2-server:3.0.1@sha256:4f3660e5f3586b81fe2e0d42d0c9fcd3c8c64268db0d3ade261dadd70d27020b";
  private static final int PORT = 8080;

  private static final GenericContainer<?> SERVER = start();

  private OidcProvider() {}

  public static String issuer() {
    return "http://%s:%d/%s".formatted(SERVER.getHost(), SERVER.getMappedPort(PORT), ISSUER_ID);
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
                        List.of(user, service)))));
    GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withEnv("JSON_CONFIG", config)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forHttp("/" + ISSUER_ID + "/.well-known/openid-configuration"));
    container.start();
    return container;
  }
}
