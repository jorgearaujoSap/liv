package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La semilla ficticia de {@code dev/seed} encaja con las migraciones y se puede repetir. */
class DevSeedTest {

  @Test
  @DisplayName("La semilla de desarrollo se aplica dos veces sobre el esquema migrado sin error")
  void seedAppliesTwice() throws Exception {
    String seed = Files.readString(Path.of("dev/seed/R__core_dev_seed.sql"));

    Object administrations =
        LivDatabase.inTx(
            OWNER,
            null,
            connection -> {
              LivDatabase.update(connection, seed);
              LivDatabase.update(connection, seed);
              return LivDatabase.rows(
                      connection,
                      "SELECT count(*) FROM core.tax_administration WHERE nit IS NULL"
                          + " AND (name LIKE '%Ipiales' OR name LIKE '%prueba B')")
                  .get(0)[0];
            });

    assertThat(administrations).isEqualTo(2L);
  }
}
