package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** La semilla ficticia de {@code dev/seed} encaja con las migraciones y se puede repetir. */
@Tag("001-17")
class DevSeedTest {

  @Test
  @DisplayName(
      "T10 · La semilla de desarrollo se aplica dos veces sobre el esquema migrado y se revierte sin dejar rastro")
  void seedAppliesTwice() throws Exception {
    String seed = Files.readString(Path.of("dev/seed/R__core_dev_seed.sql"));

    try (Connection connection = LivDatabase.connect(OWNER)) {
      connection.setAutoCommit(false);
      LivDatabase.update(connection, seed);
      LivDatabase.update(connection, seed);
      Object administrations =
          LivDatabase.rows(
                  connection,
                  "SELECT count(*) FROM core.tax_administration WHERE id IN (?, ?)",
                  UUID.fromString("10000000-0000-0000-0000-000000052356"),
                  UUID.fromString("10000000-0000-0000-0000-0000000b0001"))
              .get(0)[0];
      connection.rollback();

      assertThat(administrations).isEqualTo(2L);
    }
  }
}
