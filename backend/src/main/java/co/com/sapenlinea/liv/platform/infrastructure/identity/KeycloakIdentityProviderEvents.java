package co.com.sapenlinea.liv.platform.infrastructure.identity;

import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The events of Keycloak for the audit (plan step 9; doc. 04 §3.1: the only class that knows its
 * API). It reads {@code /admin/realms/{realm}/events} and {@code /admin-events} with the service
 * account of {@code liv-audit-importer} (client credentials, roles {@code view-events} and {@code
 * view-users}) and returns them in the neutral form of {@link IdentityProviderEvents}, with only
 * the fields of a whitelist: a role mapping gives the names of its roles; a change of a user, its
 * state; nothing else of the representations.
 *
 * <p>Keycloak filters by day: each read asks from the day of {@code from} and drops the earlier
 * events itself.
 */
public final class KeycloakIdentityProviderEvents implements IdentityProviderEvents {

  /** Failed logins, code exchange errors and lockouts; successful logins and logouts are LIV's. */
  private static final List<String> USER_TYPES =
      List.of(
          "LOGIN_ERROR",
          "CODE_TO_TOKEN_ERROR",
          "USER_DISABLED_BY_TEMPORARY_LOCKOUT",
          "USER_DISABLED_BY_PERMANENT_LOCKOUT");

  /** Changes to users and to their roles. */
  private static final List<String> RESOURCE_TYPES =
      List.of("USER", "REALM_ROLE_MAPPING", "CLIENT_ROLE_MAPPING");

  private static final String REGISTRATION = "liv-audit-importer";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  /**
   * Requests per read: Keycloak filters by day, so the events of the same day before {@code from}
   * come first and are skipped; this bounds that walk.
   */
  private static final int MAX_PAGES_PER_READ = 50;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final RestClient rest;
  private final String adminBase;

  private KeycloakIdentityProviderEvents(RestClient rest, String adminBase) {
    this.rest = rest;
    this.adminBase = adminBase;
  }

  /**
   * The adapter for the realm of {@code issuer} ({@code https://host/realms/liv}).
   *
   * @param allowHttp whether an {@code http://} issuer is accepted: only in development and tests
   *     (plan, ajustes de T12)
   */
  public static IdentityProviderEvents forProvider(
      String issuer, String clientId, String clientSecret, boolean allowHttp) {
    if (!allowHttp && !issuer.startsWith("https://")) {
      throw new IllegalStateException("The identity provider must be https: " + issuer);
    }
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new IllegalStateException(
          "The secret of " + clientId + " is missing (LIV_AUDIT_IMPORTER_SECRET)");
    }
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(REGISTRATION)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(issuer + "/protocol/openid-connect/token")
            .build();
    var registrations = new InMemoryClientRegistrationRepository(registration);
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
    var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
    interceptor.setClientRegistrationIdResolver(request -> REGISTRATION);
    var requestFactory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    requestFactory.setReadTimeout(TIMEOUT);
    RestClient rest =
        RestClient.builder().requestFactory(requestFactory).requestInterceptor(interceptor).build();
    return new KeycloakIdentityProviderEvents(rest, adminBaseOf(issuer));
  }

  /**
   * The administration API of the realm: {@code https://host/realms/liv} → {@code
   * /admin/realms/liv}.
   */
  private static String adminBaseOf(String issuer) {
    return issuer.replace("/realms/", "/admin/realms/");
  }

  @Override
  public List<ProviderEvent> userEvents(Instant from, int limit) {
    return read(
        "/events",
        uri -> uri.queryParam("type", USER_TYPES.toArray()),
        from,
        limit,
        KeycloakIdentityProviderEvents::userEvent);
  }

  @Override
  public List<ProviderEvent> adminEvents(Instant from, int limit) {
    return read(
        "/admin-events",
        uri -> uri.queryParam("resourceTypes", RESOURCE_TYPES.toArray()),
        from,
        limit,
        KeycloakIdentityProviderEvents::adminEvent);
  }

  /**
   * Up to {@code limit} events from {@code from} on, oldest first, in pages of {@code limit}.
   * Keycloak filters only by day, and in the time zone of its server: the read starts the day
   * before, in UTC, and drops the earlier events itself.
   */
  private List<ProviderEvent> read(
      String path,
      Function<UriBuilder, UriBuilder> filter,
      Instant from,
      int limit,
      Function<JsonNode, ProviderEvent> map) {
    String day = from.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1).toString();
    List<ProviderEvent> events = new ArrayList<>();
    for (int page = 0; page < MAX_PAGES_PER_READ && events.size() < limit; page++) {
      int first = page * limit;
      JsonNode got =
          rest.get()
              .uri(
                  adminBase + path,
                  uri ->
                      filter
                          .apply(uri)
                          .queryParam("dateFrom", day)
                          .queryParam("direction", "asc")
                          .queryParam("first", first)
                          .queryParam("max", limit)
                          .build())
              .retrieve()
              .body(JsonNode.class);
      if (got == null || got.isEmpty()) {
        break;
      }
      for (JsonNode node : got) {
        ProviderEvent event = map.apply(node);
        if (!event.time().isBefore(from)) {
          events.add(event);
        }
      }
      if (got.size() < limit) {
        break;
      }
    }
    events.sort(Comparator.comparing(ProviderEvent::time));
    return events.size() > limit ? List.copyOf(events.subList(0, limit)) : events;
  }

  private static ProviderEvent userEvent(JsonNode node) {
    return new ProviderEvent(
        id(node),
        Kind.USER,
        text(node, "type"),
        Instant.ofEpochMilli(node.path("time").asLong()),
        text(node, "userId"),
        text(node.path("details"), "username"),
        text(node, "ipAddress"),
        text(node, "error"),
        null,
        null,
        null);
  }

  private static ProviderEvent adminEvent(JsonNode node) {
    String resourceType = text(node, "resourceType");
    JsonNode representation = representation(node);
    boolean roleMapping = resourceType != null && resourceType.endsWith("ROLE_MAPPING");
    boolean userChange = "USER".equals(resourceType) && representation.has("enabled");
    return new ProviderEvent(
        id(node),
        Kind.ADMIN,
        resourceType + "_" + text(node, "operationType"),
        Instant.ofEpochMilli(node.path("time").asLong()),
        userOf(text(node, "resourcePath")),
        null,
        text(node.path("authDetails"), "ipAddress"),
        text(node, "error"),
        text(node.path("authDetails"), "userId"),
        roleMapping ? roles(representation) : null,
        userChange ? representation.get("enabled").asBoolean() : null);
  }

  /** The user of {@code users/{id}} or {@code users/{id}/role-mappings/...}; null for others. */
  private static String userOf(String resourcePath) {
    if (resourcePath == null || !resourcePath.startsWith("users/")) {
      return null;
    }
    String afterPrefix = resourcePath.substring("users/".length());
    int slash = afterPrefix.indexOf('/');
    return slash < 0 ? afterPrefix : afterPrefix.substring(0, slash);
  }

  /** The names of the roles of a role mapping; nothing else of it. */
  private static List<String> roles(JsonNode representation) {
    List<String> names = new ArrayList<>();
    if (representation.isArray()) {
      representation.forEach(role -> {
        if (role.hasNonNull("name")) {
          names.add(role.get("name").asString());
        }
      });
    }
    return names;
  }

  /** The representation comes as JSON inside a string, only with the details enabled. */
  private static JsonNode representation(JsonNode node) {
    String text = text(node, "representation");
    if (text == null) {
      return JSON.createObjectNode();
    }
    try {
      return JSON.readTree(text);
    } catch (RuntimeException e) {
      return JSON.createObjectNode();
    }
  }

  /** The id of the event, or the SHA-256 of the event if this version of Keycloak gives none. */
  private static String id(JsonNode node) {
    String id = text(node, "id");
    if (id != null) {
      return id;
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(node.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }
}
