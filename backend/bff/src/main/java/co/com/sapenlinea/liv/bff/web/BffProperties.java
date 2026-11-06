package co.com.sapenlinea.liv.bff.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * The configuration of the BFF that comes from the environment (README, "Configuración"). The
 * secrets and the URLs have no default value: without them the BFF does not start.
 *
 * @param oidc the identity provider: any standard OIDC server (001-20)
 * @param api liv-api, whose internal endpoints the BFF calls
 * @param session how the session is protected
 */
@ConfigurationProperties("liv.bff")
public record BffProperties(Oidc oidc, Api api, Session session) {

  public BffProperties {
    if (oidc == null || api == null || session == null) {
      throw new IllegalArgumentException(
          "Faltan las propiedades liv.bff.oidc, liv.bff.api o liv.bff.session");
    }
  }

  /**
   * @param issuerUri the issuer of the provider, whose discovery document the BFF reads
   * @param allowHttpIssuer lets an {@code http://} issuer pass, for local development and tests
   * @param clientId the client of the sign in
   * @param clientSecret its secret
   * @param serviceClientId the client of the internal calls (client credentials)
   * @param serviceClientSecret its secret
   * @param revocationUri the revocation endpoint, if the provider does not publish one
   * @param redirectUri where the provider sends the person back, with the placeholders of Spring
   */
  public record Oidc(
      String issuerUri,
      @DefaultValue("false") boolean allowHttpIssuer,
      @DefaultValue("liv-bff") String clientId,
      String clientSecret,
      @DefaultValue("liv-bff-service") String serviceClientId,
      String serviceClientSecret,
      String revocationUri,
      @DefaultValue("{baseUrl}/login/oauth2/code/{registrationId}") String redirectUri) {

    public Oidc {
      required(issuerUri, "liv.bff.oidc.issuer-uri");
      required(clientSecret, "liv.bff.oidc.client-secret");
      required(serviceClientSecret, "liv.bff.oidc.service-client-secret");
      if (!allowHttpIssuer && !issuerUri.startsWith("https://")) {
        throw new IllegalArgumentException(
            "liv.bff.oidc.issuer-uri debe ser https:// (solo desarrollo y pruebas lo permiten con"
                + " liv.bff.oidc.allow-http-issuer)");
      }
    }
  }

  /**
   * @param baseUrl the internal address of the API
   * @param connectTimeout how long to wait for a connection
   * @param readTimeout how long to wait for an answer
   */
  public record Api(
      String baseUrl,
      @DefaultValue("2s") Duration connectTimeout,
      @DefaultValue("5s") Duration readTimeout) {

    public Api {
      required(baseUrl, "liv.bff.api.base-url");
    }
  }

  /**
   * @param encryptionKey the key that encrypts the tokens in the session: 32 bytes in base64
   * @param absoluteTimeout the maximum duration of a session, whatever its activity (001-10)
   * @param sweepInterval how often the expired sessions that nobody came back to are ended (T19);
   *     its value is in {@code application.yml}, which the schedule of the sweep reads too
   * @param sweepGiveUpAfter how long the sweep keeps trying to end a session whose end LIV or the
   *     provider keep failing (T19); then it deletes it, with an error in the log
   */
  public record Session(
      String encryptionKey,
      @DefaultValue("8h") Duration absoluteTimeout,
      Duration sweepInterval,
      @DefaultValue("1d") Duration sweepGiveUpAfter) {

    public Session {
      required(encryptionKey, "liv.bff.session.encryption-key");
      if (absoluteTimeout.isZero() || absoluteTimeout.isNegative()) {
        throw new IllegalArgumentException("liv.bff.session.absolute-timeout debe ser positiva");
      }
      if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
        throw new IllegalArgumentException("liv.bff.session.sweep-interval debe ser positivo");
      }
      // The wait counts from when the session expired, and the first try can come up to an
      // interval later: with less than two, a session could go at its first failure.
      if (sweepGiveUpAfter == null
          || sweepGiveUpAfter.compareTo(sweepInterval.multipliedBy(2)) < 0) {
        throw new IllegalArgumentException(
            "liv.bff.session.sweep-give-up-after debe ser al menos dos veces"
                + " liv.bff.session.sweep-interval: si no, el barrido puede borrar una sesión al"
                + " primer fallo");
      }
    }
  }

  private static void required(String value, String property) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("Falta la propiedad " + property);
    }
  }
}
