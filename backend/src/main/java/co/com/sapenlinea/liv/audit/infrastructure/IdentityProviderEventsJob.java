package co.com.sapenlinea.liv.audit.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The scheduled import of the events of the identity provider (001-18). Pending: T15. */
@Component
@ConditionalOnProperty(name = "liv.audit.importer.enabled", havingValue = "true")
public class IdentityProviderEventsJob {

  public void run() {
    throw new UnsupportedOperationException("T15");
  }
}
