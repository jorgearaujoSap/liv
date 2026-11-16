package co.com.sapenlinea.liv.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Fila de la auditoría (007-20)")
class AuditLogEntryTest {

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · El Entry copia previousValues y newValues: cambiar el origen no los altera")
  void entryCopiesItsValues() {
    var previous = new HashMap<String, Object>(Map.of("status", "PLANEADA"));
    var current = new HashMap<String, Object>(Map.of("status", "EN_PREPARACION"));

    Entry entry = entry(previous, current);
    previous.put("status", "CAMBIADO");
    current.put("status", "CAMBIADO");

    assertThat(entry.previousValues()).isEqualTo(Map.of("status", "PLANEADA"));
    assertThat(entry.newValues()).isEqualTo(Map.of("status", "EN_PREPARACION"));
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · El Entry admite previousValues nulo y lo deja nulo")
  void entryAdmitsNullPreviousValues() {
    assertThat(entry(null, Map.of("result", "SUCCESS")).previousValues()).isNull();
  }

  private static Entry entry(Map<String, Object> previous, Map<String, Object> current) {
    return new Entry(
        "LIV",
        UUID.randomUUID().toString(),
        UUID.randomUUID(),
        null,
        "test.entity",
        null,
        "TEST",
        Instant.now(),
        null,
        null,
        previous,
        current);
  }
}
