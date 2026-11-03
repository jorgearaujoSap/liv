package co.com.sapenlinea.liv.platform.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * The two kinds of token LIV accepts, both from the same issuer and validated with its published
 * keys (signature, issuer, expiry and audience):
 *
 * <ul>
 *   <li>user tokens, with the audience of the API and the BFF as {@code azp}: users log in only
 *       through the BFF (plan › Decisiones, T12);
 *   <li>the service token of the BFF, with the internal audience and its client as {@code azp}. A
 *       user token never meets both, even if the user asks for more scopes (plan, step 8).
 * </ul>
 *
 * <p>Discovery is lazy, on the first token, and retried while it fails: the API starts even if the
 * provider is not up yet. Calls to the provider have their own timeouts, so a provider that does
 * not answer is a quick 503 and not a request waiting for minutes.
 */
final class TokenDecoders {

  private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(3);

  private TokenDecoders() {}

  static JwtDecoder userTokens(LivSecurityProperties properties) {
    return decoder(
        properties.issuerUri(),
        List.of(audience(properties.userAudience()), authorizedParty(properties.userClientId())));
  }

  static JwtDecoder serviceTokens(LivSecurityProperties properties) {
    return decoder(
        properties.issuerUri(),
        List.of(
            audience(properties.serviceAudience()), authorizedParty(properties.serviceClientId())));
  }

  private static JwtDecoder decoder(String issuer, List<OAuth2TokenValidator<Jwt>> extra) {
    return new SupplierJwtDecoder(() -> {
      NimbusJwtDecoder decoder =
          NimbusJwtDecoder.withIssuerLocation(issuer).restOperations(provider()).build();
      List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(extra);
      validators.add(new JwtIssuerValidator(issuer));
      validators.add(timestamps());
      decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
      return decoder;
    });
  }

  /**
   * {@code exp}, {@code nbf} and {@code iat} as usual, but {@code exp} is mandatory: a token
   * without it would never expire (doc. 02 §3).
   */
  private static OAuth2TokenValidator<Jwt> timestamps() {
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setAllowEmptyExpiryClaim(false);
    return timestamps;
  }

  /** The client the token was issued to ({@code azp}, OIDC). */
  private static OAuth2TokenValidator<Jwt> authorizedParty(String clientId) {
    return new JwtClaimValidator<String>(IdTokenClaimNames.AZP, clientId::equals);
  }

  private static OAuth2TokenValidator<Jwt> audience(String audience) {
    return new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(audience));
  }

  private static RestTemplate provider() {
    SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(PROVIDER_TIMEOUT);
    requests.setReadTimeout(PROVIDER_TIMEOUT);
    return new RestTemplate(requests);
  }
}
