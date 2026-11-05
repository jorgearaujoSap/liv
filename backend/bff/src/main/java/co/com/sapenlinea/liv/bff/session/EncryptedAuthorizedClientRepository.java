package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where the tokens of a person live: in the session of the server, encrypted (001-11, plan, step
 * 10). It replaces the default repository of Spring Security, which would put the tokens in the
 * session as they are. The access and refresh tokens are written as JSON, encrypted with {@link
 * TokenCipher} and kept as one attribute of the session, so the rest of Spring Security (the token
 * relay and the renewal of T18) uses them as always.
 *
 * <p>A session holds the tokens of one client, the sign in. A value that cannot be decrypted is
 * treated as no tokens, and the session is no longer usable.
 */
public final class EncryptedAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

  static final String ATTRIBUTE = "LIV_TOKENS";

  private static final Logger LOG =
      LoggerFactory.getLogger(EncryptedAuthorizedClientRepository.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** What is stored: the tokens and when they were issued and expire, in epoch seconds. */
  record Stored(
      String registrationId,
      String principalName,
      String accessToken,
      long accessIssuedAt,
      long accessExpiresAt,
      Set<String> scopes,
      String refreshToken,
      Long refreshIssuedAt) {}

  private final TokenCipher cipher;
  private final ClientRegistrationRepository registrations;

  public EncryptedAuthorizedClientRepository(
      TokenCipher cipher, ClientRegistrationRepository registrations) {
    this.cipher = cipher;
    this.registrations = registrations;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String registrationId, Authentication principal, HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof byte[] encrypted)) {
      return null;
    }
    Stored stored;
    try {
      stored = JSON.readValue(cipher.decrypt(encrypted), Stored.class);
    } catch (IllegalStateException | JacksonException e) {
      LOG.error("Los tokens de la sesión no se pueden leer: {}", e.getClass().getSimpleName());
      return null;
    }
    if (!stored.registrationId().equals(registrationId)
        || (principal != null && !stored.principalName().equals(principal.getName()))) {
      // Tokens of another client, or of another person: never returned.
      return null;
    }
    ClientRegistration registration = registrations.findByRegistrationId(registrationId);
    return registration == null ? null : (T) toClient(registration, stored);
  }

  @Override
  public void saveAuthorizedClient(
      OAuth2AuthorizedClient client,
      Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    OAuth2AccessToken access = client.getAccessToken();
    OAuth2RefreshToken refresh = client.getRefreshToken();
    Stored stored =
        new Stored(
            client.getClientRegistration().getRegistrationId(),
            client.getPrincipalName(),
            access.getTokenValue(),
            access.getIssuedAt().getEpochSecond(),
            access.getExpiresAt().getEpochSecond(),
            new LinkedHashSet<>(access.getScopes()),
            refresh == null ? null : refresh.getTokenValue(),
            refresh == null || refresh.getIssuedAt() == null
                ? null
                : refresh.getIssuedAt().getEpochSecond());
    request.getSession().setAttribute(ATTRIBUTE, cipher.encrypt(JSON.writeValueAsBytes(stored)));
  }

  @Override
  public void removeAuthorizedClient(
      String registrationId,
      Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(ATTRIBUTE);
    }
  }

  private static OAuth2AuthorizedClient toClient(ClientRegistration registration, Stored stored) {
    OAuth2AccessToken access =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            stored.accessToken(),
            Instant.ofEpochSecond(stored.accessIssuedAt()),
            Instant.ofEpochSecond(stored.accessExpiresAt()),
            stored.scopes());
    OAuth2RefreshToken refresh =
        stored.refreshToken() == null
            ? null
            : new OAuth2RefreshToken(
                stored.refreshToken(),
                stored.refreshIssuedAt() == null
                    ? null
                    : Instant.ofEpochSecond(stored.refreshIssuedAt()));
    return new OAuth2AuthorizedClient(registration, stored.principalName(), access, refresh);
  }
}
