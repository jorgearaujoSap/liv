package co.com.sapenlinea.liv.architecture;

import co.com.sapenlinea.liv.LivApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  private static final ApplicationModules MODULES = ApplicationModules.of(LivApplication.class);

  @Test
  @DisplayName("Los módulos respetan sus fronteras y no tienen ciclos (doc. 01 §3.2)")
  void modulesRespectTheirBoundaries() {
    MODULES.verify();
  }
}
