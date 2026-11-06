package co.com.sapenlinea.liv.bff.logout;

import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;
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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
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
 *   <li>with the event of the standard in {@code events}, an id ({@code jti}), a {@code sid} and no
 *       {@code nonce} (which only an id token has);
 *   <li>of type {@code logout+jwt} or {@code JWT}, signed with RS256.
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

  /**
   * What the token says to end, once checked: the session {@code sid} of the provider {@code
   * issuer}. The {@code sid} is required: the realm sends it ({@code
   * backchannel.logout.session.required}), and a token with only the {@code sub} would end every
   * session of the person, also the ones opened after it, if it were sent again.
   */
  record LogoutToken(String issuer, String sid) {}

  /**
   * @throws org.springframework.security.oauth2.jwt.BadJwtException if it is not a valid logout
   *     token for this client
   * @throws org.springframework.security.oauth2.jwt.JwtException if the keys of the provider cannot
   *     be read
   */
  LogoutToken decode(String token) {
    Jwt jwt = decoder.obtain().decode(token);
    return new LogoutToken(jwt.getClaimAsString("iss"), jwt.getClaimAsString("sid"));
  }

  private JwtDecoder build() {
    ClientRegistration registration = registrations.findByRegistrationId(OidcRegistrations.USER);
    NimbusJwtDecoder built =
        NimbusJwtDecoder.withJwkSource(keysOf(registration))
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            // Spring checks no typ by default: the standard asks for logout+jwt, and accepts JWT.
            .jwtProcessorCustomizer(processor ->
                processor.setJWSTypeVerifier(
                    new DefaultJOSEObjectTypeVerifier<>(
                        new JOSEObjectType("logout+jwt"), JOSEObjectType.JWT, null)))
            .build();
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

  /**
   * The keys of the provider, from its {@code jwks_uri}: read with short timeouts, kept for 15
   * minutes and read again at most every 30 seconds, also when a token names an unknown key. A
   * token sent only to make the BFF read the keys again cannot flood the provider.
   */
  private static JWKSource<SecurityContext> keysOf(ClientRegistration registration) {
    try {
      return JWKSourceBuilder.create(
              URI.create(registration.getProviderDetails().getJwkSetUri()).toURL(),
              new DefaultResourceRetriever(2_000, 5_000, 512 * 1024))
          .cache(Duration.ofMinutes(15).toMillis(), Duration.ofSeconds(15).toMillis())
          .rateLimited(Duration.ofSeconds(30).toMillis())
          .refreshAheadCache(false)
          .retrying(false)
          .build();
    } catch (MalformedURLException | IllegalArgumentException e) {
      throw new IllegalStateException("El jwks_uri del proveedor no es una URL", e);
    }
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
      if (token.getClaimAsString("sid") == null) {
        failures.add("falta sid");
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
