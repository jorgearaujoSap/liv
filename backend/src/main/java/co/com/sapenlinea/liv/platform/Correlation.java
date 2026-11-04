package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import org.slf4j.MDC;

/**
 * The correlation id of the current request (doc. 02 §9), which the correlation filter puts in the
 * MDC. Events carry it, so their audit rows keep it although a listener writes them later, in
 * another thread (001-18).
 */
public final class Correlation {

  /** MDC key of the correlation id; the log pattern prints it ({@code logging.pattern}). */
  public static final String MDC_KEY = "correlation_id";

  private Correlation() {}

  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }
}
