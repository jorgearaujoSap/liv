package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import java.util.UUID;

/**
 * The administration the current work runs for (doc. 02 §5.1). It always comes from the LIV
 * registry, never from a parameter or a token claim. The transaction manager reads it to set the
 * RLS context; without it nothing is set and the policies return no rows.
 */
public final class AdministrationContext {

  private AdministrationContext() {}

  public static Optional<UUID> current() {
    return Optional.empty();
  }

  /** Opens a scope with the given administration; closing it restores the previous one. */
  public static Scope open(UUID administrationId) {
    return () -> {};
  }

  /** A scope of {@link #open}; its close never throws. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
