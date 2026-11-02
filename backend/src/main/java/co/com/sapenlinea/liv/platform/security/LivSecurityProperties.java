package co.com.sapenlinea.liv.platform.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token validation, by properties only (plan, step 7): the issuer is discovered with OIDC, so any
 * standard provider works (D-001-6, 001-20).
 *
 * @param issuerUri issuer of user and service tokens; its discovery document gives the JWKS
 * @param userAudience audience every user token must have ({@code /api/**}, {@code X-User-Token})
 * @param serviceAudience audience of the BFF service token ({@code /internal/**})
 * @param serviceClientId {@code azp} of the service token; no user token has it
 * @param claims names of the LIV claims in the token
 */
@Validated
@ConfigurationProperties("liv.security")
record LivSecurityProperties(
    @NotBlank String issuerUri,
    @NotBlank String userAudience,
    @NotBlank String serviceAudience,
    @NotBlank String serviceClientId,
    @Valid @NotNull Claims claims) {

  /**
   * @param administrationId claim with the administration of the user
   * @param roles claim with the list of roles
   */
  record Claims(@NotBlank String administrationId, @NotBlank String roles) {}
}
