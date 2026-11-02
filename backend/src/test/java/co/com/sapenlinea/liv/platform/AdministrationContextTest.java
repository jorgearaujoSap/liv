package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Contexto de administración (T12)")
class AdministrationContextTest {

  private static final UUID A = UUID.randomUUID();
  private static final UUID B = UUID.randomUUID();

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Sin un alcance abierto no hay administración")
  void emptyByDefault() {
    assertThat(AdministrationContext.current()).isEmpty();
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Al cerrar el alcance se restaura el anterior, también anidado")
  void scopesNestAndRestore() {
    try (var outer = AdministrationContext.open(A)) {
      assertThat(AdministrationContext.current()).contains(A);
      try (var inner = AdministrationContext.open(B)) {
        assertThat(AdministrationContext.current()).contains(B);
      }
      assertThat(AdministrationContext.current()).contains(A);
    }
    assertThat(AdministrationContext.current()).isEmpty();
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · El contexto no pasa a otro hilo")
  void doesNotLeakToOtherThreads() {
    try (var scope = AdministrationContext.open(A)) {
      assertThat(CompletableFuture.supplyAsync(AdministrationContext::current).join()).isEmpty();
    }
  }
}
