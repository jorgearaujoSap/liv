package co.com.sapenlinea.liv.bff.session;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Revokes the refresh token of a session in the identity provider (RFC 7009). That also closes the
 * session of the person in the provider (spike T06), so the browser never needs an {@code
 * id_token_hint} to sign out. The endpoint is the one the provider publishes in its discovery
 * document, unless the configuration names another.
 */
public final class RefreshTokenRevoker {

  private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenRevoker.class);

  private final RestClient http;
  private final String configuredEndpoint;

  public RefreshTokenRevoker(RestClient http, String configuredEndpoint) {
    this.http = http;
    this.configuredEndpoint = configuredEndpoint;
  }

  /**
   * Revokes the refresh token of the client. It never throws: a session ends even if the provider
   * does not answer, whatever goes wrong (no refresh token, no endpoint, a failed call).
   */
  public void revoke(OAuth2AuthorizedClient client) {
    if (client.getRefreshToken() == null) {
      LOG.warn("La sesión no tiene refresh token: no hay nada que revocar en el proveedor");
      return;
    }
    ClientRegistration registration = client.getClientRegistration();
    String endpoint = endpointOf(registration);
    if (endpoint == null) {
      LOG.error("El proveedor no publica un endpoint de revocación y no se configuró uno");
      return;
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", client.getRefreshToken().getTokenValue());
    form.add("token_type_hint", "refresh_token");
    try {
      post(registration, endpoint, form);
    } catch (RuntimeException e) {
      LOG.warn("La revocación en el proveedor falló: {}", e.getClass().getSimpleName());
    }
  }

  private void post(
      ClientRegistration registration, String endpoint, MultiValueMap<String, String> form) {
    http.post()
        .uri(endpoint)
        .headers(headers ->
            headers.setBasicAuth(
                formEncoded(registration.getClientId()),
                formEncoded(registration.getClientSecret())))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toBodilessEntity();
  }

  private String endpointOf(ClientRegistration registration) {
    if (StringUtils.hasText(configuredEndpoint)) {
      return configuredEndpoint;
    }
    Object published =
        registration.getProviderDetails().getConfigurationMetadata().get("revocation_endpoint");
    return published == null ? null : published.toString();
  }

  /** RFC 6749 §2.3.1: the client id and secret are form-encoded before the Basic encoding. */
  private static String formEncoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
