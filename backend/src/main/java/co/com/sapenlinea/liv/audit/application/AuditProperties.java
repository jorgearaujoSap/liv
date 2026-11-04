package co.com.sapenlinea.liv.audit.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The audit (001-18, T15).
 *
 * @param hmacKey the secret key of the HMAC that masks attempted usernames and the authors of the
 *     provider's changes ({@code LIV_AUDIT_HMAC_KEY}), at least 32 characters
 * @param importer the importer of the events of the identity provider
 */
@ConfigurationProperties("liv.audit")
public record AuditProperties(String hmacKey, Importer importer) {

  /**
   * The importer of the events of the identity provider.
   *
   * @param enabled whether it runs; off only in tests and environments without the provider
   * @param overlap how far back each import reads again: the provider may store an event late
   * @param maxLag how old the watermark may get before the importer reports it is behind
   * @param retention how far back the first import reads: the provider deletes older events
   * @param pageSize events per request to the provider
   * @param maxPages requests per stream and run: the rest waits for the next run
   */
  public record Importer(
      boolean enabled,
      Duration overlap,
      Duration maxLag,
      Duration retention,
      int pageSize,
      int maxPages) {}
}
