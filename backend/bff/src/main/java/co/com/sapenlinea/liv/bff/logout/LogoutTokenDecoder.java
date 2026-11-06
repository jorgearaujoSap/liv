package co.com.sapenlinea.liv.bff.logout;

import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.function.SingletonSupplier;

/**
 * Reads a logout token of the provider and checks it as OpenID Connect Back-Channel Logout 1.0
 * (§2.6) says, with no API of any provider (001-20):
 *
 * <ul>
 *   <li>signed with a key the provider publishes ({@code jwks_uri} of its discovery document);
 *   <li>issued by the provider of the sign in ({@code iss}) for this client ({@code aud});
 *   <li>issued now: {@code iat} within {@link #MAX_AGE}, and not expired if it says when;
 *   <li>with the event of the standard in {@code events}, an id ({@code jti}), a {@code sid} or a
 *       {@code sub}, and no {@code nonce} (which only an id token has).
 * </ul>
 *
 * The keys are read the first time a token arrives, not at start: the BFF starts without the
 * provider.
 */
final class LogoutTokenDecoder {

  static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

  /** How old a logout token may be: the provider sends it when the session ends. */
  static final Duration MAX_AGE = Duration.ofMinutes(2);

  /** The difference between the clocks of the provider and of the BFF that is tolerated. */
  private static final Duration SKEW = Duration.ofSeconds(60);

  private final ClientRegistrationRepository registrations;
  private final Clock clock;
  private final SingletonSupplier<JwtDecoder> decoder = SingletonSupplier.of(this::build);

  LogoutTokenDecoder(ClientRegistrationRepository registrations, Clock clock) {
    this.registrations = registrations;
    this.clock = clock;
  }

  /** What the token says to end, once checked. */
  record LogoutToken(String issuer, String sid, String subject) {}

  /**
   * @throws org.springframework.security.oauth2.jwt.BadJwtException if it is not a valid logout
   *     token for this client
   * @throws org.springframework.security.oauth2.jwt.JwtException if the keys of the provider cannot
   *     be read
   */
  LogoutToken decode(String token) {
    Jwt jwt = decoder.obtain().decode(token);
    return new LogoutToken(
        jwt.getClaimAsString("iss"), jwt.getClaimAsString("sid"), jwt.getSubject());
  }

  private JwtDecoder build() {
    ClientRegistration registration = registrations.findByRegistrationId(OidcRegistrations.USER);
    NimbusJwtDecoder built =
        NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri()).build();
    JwtTimestampValidator expiry = new JwtTimestampValidator(SKEW);
    expiry.setClock(clock);
    built.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            expiry,
            new LogoutTokenValidator(
                registration.getProviderDetails().getIssuerUri(),
                registration.getClientId(),
                clock)));
    return built;
  }

  /** The claims of §2.4 and §2.6 of the standard; every failure is said in the error. */
  private record LogoutTokenValidator(String issuer, String clientId, Clock clock)
      implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
      List<String> failures = new ArrayList<>();
      if (!issuer.equals(token.getClaimAsString("iss"))) {
        failures.add("iss no es el del proveedor");
      }
      List<String> audience = token.getAudience();
      if (audience == null || !audience.contains(clientId)) {
        failures.add("aud no incluye a " + clientId);
      }
      Instant issuedAt = token.getIssuedAt();
      Instant now = clock.instant();
      if (issuedAt == null
          || issuedAt.isAfter(now.plus(SKEW))
          || issuedAt.isBefore(now.minus(MAX_AGE).minus(SKEW))) {
        failures.add("iat falta o no es de ahora");
      }
      if (!(token.getClaim("events") instanceof Map<?, ?> events)
          || !(events.get(EVENT) instanceof Map<?, ?>)) {
        failures.add("events no trae el evento de cierre por canal trasero");
      }
      if (token.getId() == null) {
        failures.add("falta jti");
      }
      if (token.getClaimAsString("sid") == null && token.getSubject() == null) {
        failures.add("falta sid y sub");
      }
      if (token.hasClaim("nonce")) {
        failures.add("trae nonce");
      }
      return failures.isEmpty()
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(
              failures.stream()
                  .map(failure -> new OAuth2Error("invalid_request", failure, null))
                  .toList());
    }
  }
}
