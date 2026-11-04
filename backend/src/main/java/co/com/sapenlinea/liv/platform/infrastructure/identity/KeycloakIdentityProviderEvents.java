package co.com.sapenlinea.liv.platform.infrastructure.identity;

import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

/**
 * The events of Keycloak for the audit (plan step 9; doc. 04 §3.1: the only class that knows its
 * API). It reads {@code /admin/realms/{realm}/events} and {@code /admin-events} with the service
 * account of {@code liv-audit-importer} (client credentials) and returns them in the neutral form
 * of {@link IdentityProviderEvents}, with only the fields of a whitelist: nothing of the
 * representations of the changes is read.
 *
 * <p>Keycloak 26 filters from an instant ({@code dateFrom} in epoch milliseconds) and returns the
 * oldest first ({@code direction=asc}); the pages go by offset. Every call to Keycloak, the token
 * included, has a timeout.
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

  private final RestClient rest;
  private final String adminBase;
  private final int pageSize;

  private KeycloakIdentityProviderEvents(RestClient rest, String adminBase, int pageSize) {
    this.rest = rest;
    this.adminBase = adminBase;
    this.pageSize = pageSize;
  }

  /**
   * The adapter for the realm of {@code issuer} ({@code https://host/realms/liv}).
   *
   * @param adminBase the administration API of the realm, for example on the internal network; null
   *     or blank to derive it from the issuer ({@code https://host/admin/realms/liv})
   * @param allowHttp whether an {@code http://} issuer is accepted: only in development and tests
   *     (plan, ajustes de T12)
   * @param pageSize events per request to Keycloak
   */
  public static IdentityProviderEvents forProvider(
      String issuer,
      String adminBase,
      String clientId,
      String clientSecret,
      boolean allowHttp,
      int pageSize) {
    String admin = adminBase == null || adminBase.isBlank() ? adminBaseOf(issuer) : adminBase;
    for (String url : List.of(issuer, admin)) {
      if (!allowHttp && !url.startsWith("https://")) {
        throw new IllegalStateException("The identity provider must be https: " + url);
      }
    }
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new IllegalStateException(
          "The secret of " + clientId + " is missing (LIV_AUDIT_IMPORTER_SECRET)");
    }
    JdkClientHttpRequestFactory requests = requestsWithTimeouts();
    RestClient rest =
        RestClient.builder()
            .requestFactory(requests)
            .requestInterceptor(tokens(issuer, clientId, clientSecret, requests))
            .build();
    return new KeycloakIdentityProviderEvents(rest, admin, pageSize);
  }

  @Override
  public EventsPage userEvents(Instant from, int limit) {
    return read(
        "/events",
        uri -> uri.queryParam("type", USER_TYPES.toArray()),
        from,
        limit,
        KeycloakIdentityProviderEvents::userEvent);
  }

  @Override
  public EventsPage adminEvents(Instant from, int limit) {
    return read(
        "/admin-events",
        uri -> uri.queryParam("resourceTypes", RESOURCE_TYPES.toArray()),
        from,
        limit,
        KeycloakIdentityProviderEvents::adminEvent);
  }

  /** Up to {@code limit} (positive) events from {@code from} on, oldest first, page by page. */
  private EventsPage read(
      String path,
      Function<UriBuilder, UriBuilder> filter,
      Instant from,
      int limit,
      Function<JsonNode, ProviderEvent> map) {
    List<ProviderEvent> events = new ArrayList<>();
    while (events.size() < limit) {
      int first = events.size();
      int max = Math.min(pageSize, limit - events.size());
      JsonNode page =
          rest.get()
              .uri(
                  adminBase + path,
                  uri ->
                      filter
                          .apply(uri)
                          .queryParam("dateFrom", from.toEpochMilli())
                          .queryParam("direction", "asc")
                          .queryParam("first", first)
                          .queryParam("max", max)
                          .build())
              .retrieve()
              .body(JsonNode.class);
      int received = page == null ? 0 : page.size();
      if (page != null) {
        page.forEach(node -> events.add(map.apply(node)));
      }
      if (received < max) {
        return new EventsPage(events, false);
      }
    }
    // A full last page: Keycloak may have more, which the next read takes.
    return new EventsPage(events, true);
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
        null);
  }

  private static ProviderEvent adminEvent(JsonNode node) {
    return new ProviderEvent(
        id(node),
        Kind.ADMIN,
        text(node, "resourceType") + "_" + text(node, "operationType"),
        Instant.ofEpochMilli(node.path("time").asLong()),
        userOf(text(node, "resourcePath")),
        null,
        text(node.path("authDetails"), "ipAddress"),
        text(node, "error"),
        text(node.path("authDetails"), "userId"));
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

  /** Keycloak 26 gives every event its id; without one, it could not be stored once. */
  private static String id(JsonNode node) {
    String id = text(node, "id");
    if (id == null) {
      throw new IllegalStateException("An event of Keycloak came without its id");
    }
    return id;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }

  /** The administration API of the realm: {@code /realms/liv} → {@code /admin/realms/liv}. */
  private static String adminBaseOf(String issuer) {
    int realms = issuer.lastIndexOf("/realms/");
    return issuer.substring(0, realms) + "/admin" + issuer.substring(realms);
  }

  private static JdkClientHttpRequestFactory requestsWithTimeouts() {
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    requests.setReadTimeout(TIMEOUT);
    return requests;
  }

  /** The token of the service account, by client credentials, with the same timeouts. */
  private static OAuth2ClientHttpRequestInterceptor tokens(
      String issuer, String clientId, String clientSecret, JdkClientHttpRequestFactory requests) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(REGISTRATION)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(issuer + "/protocol/openid-connect/token")
            .build();
    var tokenClient = new RestClientClientCredentialsTokenResponseClient();
    tokenClient.setRestClient(
        RestClient.builder()
            .requestFactory(requests)
            .messageConverters(converters -> {
              converters.addFirst(new FormHttpMessageConverter());
              converters.addFirst(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
    provider.setAccessTokenResponseClient(tokenClient);
    var registrations = new InMemoryClientRegistrationRepository(registration);
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
    manager.setAuthorizedClientProvider(provider);
    var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
    interceptor.setClientRegistrationIdResolver(request -> REGISTRATION);
    return interceptor;
  }
}
