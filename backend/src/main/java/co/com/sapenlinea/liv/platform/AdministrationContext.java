package co.com.sapenlinea.liv.platform;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The administration the current work runs for (doc. 02 §5.1). It always comes from the LIV
 * registry, never from a parameter or a token claim. The transaction manager sets it as the RLS
 * context at the start of every transaction; without it the context is empty and the policies
 * return no rows.
 *
 * <p>It belongs to the thread: the security filter opens it for each request, and asynchronous
 * listeners and jobs open it with the administration of their event before their transaction.
 * Opening another administration inside an open transaction fails: the database would keep
 * filtering by the one the transaction started with.
 *
 * <p>It does not travel to other threads: asynchronous controllers and {@code @Async} methods run
 * without it until a propagator exists. Only {@code platform} and infrastructure adapters may open
 * it (ArchitectureRulesTest).
 */
public final class AdministrationContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private AdministrationContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /**
   * Opens a scope with the given administration; closing it restores the previous one.
   *
   * @throws IllegalStateException if a transaction is active and the current administration is not
   *     the requested one (also when there is none)
   */
  public static Scope open(UUID administrationId) {
    UUID previous = CURRENT.get();
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && !Objects.equals(previous, administrationId)) {
      throw new IllegalStateException(
          "Open the administration context before the transaction, not inside it");
    }
    CURRENT.set(administrationId);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  /**
   * Removes a context that earlier work left open on this thread and returns it, so the thread can
   * serve the next request. LeakedContextFilter calls it; ArchitectureRulesTest limits who may.
   */
  public static Optional<UUID> discardLeaked() {
    Optional<UUID> leaked = current();
    CURRENT.remove();
    return leaked;
  }

  /** A scope of {@link #open}; its close never throws. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
