package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import java.util.UUID;

/**
 * The administration the current work runs for (doc. 02 §5.1). It always comes from the LIV
 * registry, never from a parameter or a token claim. The transaction manager reads it to set the
 * RLS context; without it nothing is set and the policies return no rows.
 *
 * <p>It belongs to the thread: the security filter opens it for each request, and asynchronous
 * listeners and jobs open it with the administration of their event before their transaction.
 */
public final class AdministrationContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private AdministrationContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** Opens a scope with the given administration; closing it restores the previous one. */
  public static Scope open(UUID administrationId) {
    UUID previous = CURRENT.get();
    CURRENT.set(administrationId);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  /** A scope of {@link #open}; its close never throws. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
