package co.com.sapenlinea.liv.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * The two kinds of token LIV accepts, both from the same issuer and validated with its published
 * keys (signature, issuer, expiry and audience):
 *
 * <ul>
 *   <li>user tokens, with the audience of the API;
 *   <li>the service token of the BFF, with the internal audience and its client as {@code azp}. A
 *       user token never meets both, even if the user asks for more scopes (plan, step 8).
 * </ul>
 *
 * <p>Discovery is lazy, on the first token: the API starts even if the provider is not up yet.
 */
final class TokenDecoders {

  private TokenDecoders() {}

  static JwtDecoder userTokens(LivSecurityProperties properties) {
    return decoder(properties.issuerUri(), List.of(audience(properties.userAudience())));
  }

  static JwtDecoder serviceTokens(LivSecurityProperties properties) {
    return decoder(
        properties.issuerUri(),
        List.of(
            audience(properties.serviceAudience()),
            new JwtClaimValidator<String>(
                IdTokenClaimNames.AZP, properties.serviceClientId()::equals)));
  }

  private static JwtDecoder decoder(String issuer, List<OAuth2TokenValidator<Jwt>> extra) {
    return new SupplierJwtDecoder(() -> {
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
      List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(extra);
      validators.add(new JwtIssuerValidator(issuer));
      decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
      return decoder;
    });
  }

  private static OAuth2TokenValidator<Jwt> audience(String audience) {
    return new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(audience));
  }
}
