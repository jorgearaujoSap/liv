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

  /** Kinds of logout token: the right one, and others that must be refused or change nothing. */
  public static final String LOGOUT = "logout";

  public static final String LOGOUT_OTHER_SESSION = "logout-otra-sesion";
  public static final String LOGOUT_WITHOUT_EVENT = "logout-sin-evento";
  public static final String LOGOUT_OTHER_CLIENT = "logout-otro-cliente";
  public static final String LOGOUT_WITH_NONCE = "logout-con-nonce";

  private static final String ISSUER_ID = "liv";
  private static final String ISSUER_WITHOUT_SID = "liv-sin-sid";
  private static final String ISSUER_SHORT_TOKENS = "liv-tokens-cortos";
  private static final String IMAGE =
      "ghcr.io/navikt/mock-oauth2-server:3.0.1@sha256:4f3660e5f3586b81fe2e0d42d0c9fcd3c8c64268db0d3ade261dadd70d27020b";
  private static final int PORT = 8080;

  private static final GenericContainer<?> SERVER = start();

  private OidcProvider() {}

  public static String issuer() {
    return "http://%s:%d/%s".formatted(SERVER.getHost(), SERVER.getMappedPort(PORT), ISSUER_ID);
  }

  /** An issuer whose tokens last 30 seconds, less than the minute before which they are renewed. */
  public static String issuerWithShortTokens() {
    return "http://%s:%d/%s"
        .formatted(SERVER.getHost(), SERVER.getMappedPort(PORT), ISSUER_SHORT_TOKENS);
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
    // Logout tokens (OpenID Connect Back-Channel Logout 1.0), as the provider sends them to
    // /bff/backchannel-logout: signed with the key of the issuer, for the client of the sign in. A
    // test gets one with logoutToken(kind); each kind is a token request with that scope.
    Map<String, Object> event =
        Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of());
    Map<String, Object> logout =
        logoutMapping(
            LOGOUT, Map.of("sub", SUBJECT, "sid", SESSION_ID, "aud", USER_CLIENT, "events", event));
    Map<String, Object> logoutOtherSession =
        logoutMapping(
            LOGOUT_OTHER_SESSION,
            Map.of("sub", SUBJECT, "sid", "otro-sid", "aud", USER_CLIENT, "events", event));
    Map<String, Object> logoutWithoutEvent =
        logoutMapping(
            LOGOUT_WITHOUT_EVENT, Map.of("sub", SUBJECT, "sid", SESSION_ID, "aud", USER_CLIENT));
    Map<String, Object> logoutOtherClient =
        logoutMapping(
            LOGOUT_OTHER_CLIENT,
            Map.of("sub", SUBJECT, "sid", SESSION_ID, "aud", "otro-cliente", "events", event));
    Map<String, Object> logoutWithNonce =
        logoutMapping(
            LOGOUT_WITH_NONCE,
            Map.of(
                "sub",
                SUBJECT,
                "sid",
                SESSION_ID,
                "aud",
                USER_CLIENT,
                "events",
                event,
                "nonce",
                "n-1"));
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
                        List.of(
                            logout,
                            logoutOtherSession,
                            logoutWithoutEvent,
                            logoutOtherClient,
                            logoutWithNonce,
                            user,
                            service)),
                    Map.of(
                        "issuerId",
                        ISSUER_WITHOUT_SID,
                        "tokenExpiry",
                        300,
                        "requestMappings",
                        List.of(withoutSid, service)),
                    // Tokens of 30 seconds: always about to expire, so the BFF renews them.
                    Map.of(
                        "issuerId",
                        ISSUER_SHORT_TOKENS,
                        "tokenExpiry",
                        30,
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

  private static Map<String, Object> logoutMapping(String kind, Map<String, Object> claims) {
    return Map.of("requestParam", "scope", "match", kind, "claims", claims);
  }

  /**
   * A logout token of the given kind, signed by the provider of {@link #issuer()}, as it would send
   * it to the BFF when the session ends there.
   */
  public static String logoutToken(String kind) {
    try {
      java.net.http.HttpResponse<String> response =
          java.net.http.HttpClient.newHttpClient()
              .send(
                  java.net.http.HttpRequest.newBuilder(java.net.URI.create(issuer() + "/token"))
                      .header("Content-Type", "application/x-www-form-urlencoded")
                      .POST(
                          java.net.http.HttpRequest.BodyPublishers.ofString(
                              "grant_type=client_credentials&client_id=proveedor"
                                  + "&client_secret=prueba&scope="
                                  + kind))
                      .build(),
                  java.net.http.HttpResponse.BodyHandlers.ofString());
      return (String) JSONObjectUtils.parse(response.body()).get("access_token");
    } catch (java.io.IOException | java.text.ParseException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
