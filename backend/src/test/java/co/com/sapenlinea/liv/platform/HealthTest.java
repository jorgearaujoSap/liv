package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The probes are public and the readiness one includes the database (doc. 03, T12). */
@DisplayName("Sondas de salud (T12)")
class HealthTest extends PlatformWebTest {

  @Test
  @DisplayName("Doc. 03 · Con la base y liv_app la instancia está lista")
  void readyWithDatabase() {
    assertThat(get("/actuator/health/readiness", Map.of()).statusCode()).isEqualTo(200);
    assertThat(get("/actuator/health/liveness", Map.of()).statusCode()).isEqualTo(200);
  }
}
