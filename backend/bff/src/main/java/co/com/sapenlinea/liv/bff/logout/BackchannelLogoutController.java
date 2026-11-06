package co.com.sapenlinea.liv.bff.logout;

import co.com.sapenlinea.liv.bff.logout.LogoutTokenDecoder.LogoutToken;
import co.com.sapenlinea.liv.bff.session.ProviderLogout;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The provider ends a session of a person on the back channel (OpenID Connect Back-Channel Logout
 * 1.0, 001-09; plan, step 6; T19). It calls {@code POST /bff/backchannel-logout} with a {@code
 * logout_token}, without a session cookie nor an anti-CSRF token: the token, signed by the
 * provider, is the only thing that is trusted ({@link LogoutTokenDecoder}). The sessions of the BFF
 * of that session of the provider end at once ({@link ProviderLogout}). The answer is the one of
 * the standard: 200, or 400 with {@code invalid_request} if the token is not valid; 503 if the keys
 * of the provider cannot be read. The route is meant for the internal network only: it is not
 * published on the internet (plan, step 6).
 */
@RestController
final class BackchannelLogoutController {

  static final String PATH = "/bff/backchannel-logout";

  /** The longest body read: a logout token is a few kilobytes. */
  private static final int MAX_BODY = 16 * 1024;

  private static final Logger LOG = LoggerFactory.getLogger(BackchannelLogoutController.class);

  private final LogoutTokenDecoder decoder;
  private final ProviderLogout logout;

  BackchannelLogoutController(
      ClientRegistrationRepository registrations, ProviderLogout logout, Clock clock) {
    this.decoder = new LogoutTokenDecoder(registrations, clock);
    this.logout = logout;
  }

  @PostMapping(
      path = PATH,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, String>> logout(HttpServletRequest request) throws IOException {
    String logoutToken = logoutTokenOf(request);
    if (!StringUtils.hasText(logoutToken)) {
      return refused("Falta logout_token");
    }
    LogoutToken token;
    try {
      token = decoder.decode(logoutToken);
    } catch (BadJwtException e) {
      LOG.warn("Se rechazó un logout token: {}", e.getMessage());
      return refused("El logout token no es válido");
    } catch (JwtException e) {
      LOG.error("No se pudieron leer las claves del proveedor: {}", e.getClass().getSimpleName());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .cacheControl(CacheControl.noStore())
          .build();
    }
    logout.end(token.issuer(), token.sid(), token.subject());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
  }

  /**
   * The {@code logout_token} of the form, read from the body. Not from the parameters: the form
   * filter of the gateway (which forwards {@code /api/**}) rewrites the body of every form and
   * hides its parameters, and Spring MVC reads a form body from them. A logout token is a few
   * kilobytes; a longer body is not one.
   */
  private static String logoutTokenOf(HttpServletRequest request) throws IOException {
    byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
    if (body.length > MAX_BODY) {
      return null;
    }
    for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
      int equals = pair.indexOf('=');
      if (equals > 0
          && URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8)
              .equals("logout_token")) {
        return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static ResponseEntity<Map<String, String>> refused(String description) {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("error", "invalid_request", "error_description", description));
  }
}
