package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** The importer of the events of the identity provider (001-18). Pending: T15. */
@Service
@ConditionalOnProperty(name = "liv.audit.importer.enabled", havingValue = "true")
public class ImportIdentityProviderEvents {

  /**
   * What an import did.
   *
   * @param imported the events written to the audit
   * @param cursor the watermark after the import
   * @param behind whether the watermark is older than the allowed lag
   */
  public record ImportResult(int imported, Instant cursor, boolean behind) {}

  @OpenUseCase("Trabajo programado de plataforma: corre sin usuario ni petición (T15)")
  public ImportResult importNew() {
    throw new UnsupportedOperationException("T15");
  }
}
