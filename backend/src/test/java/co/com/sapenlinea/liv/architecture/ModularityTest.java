package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.LivApplication;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  private static final ApplicationModules MODULES = ApplicationModules.of(LivApplication.class);

  @Test
  @DisplayName("Los módulos respetan sus fronteras y no tienen ciclos (doc. 01 §3.2)")
  void modulesRespectTheirBoundaries() {
    MODULES.verify();
  }

  @Test
  @DisplayName("ADR 0025 · Las flechas entre módulos son las aprobadas y nadie depende de audit")
  void arrowsAreTheApprovedOnes() {
    assertThat(dependenciesOf("audit")).containsExactlyInAnyOrder("core", "platform");
    assertThat(dependenciesOf("core")).containsExactly("platform");
    assertThat(dependenciesOf("platform")).isEmpty();
    for (ApplicationModule module : MODULES) {
      assertThat(dependenciesOf(name(module))).as(name(module)).doesNotContain("audit");
    }
  }

  private static Set<String> dependenciesOf(String module) {
    return MODULES
        .getModuleByName(module)
        .orElseThrow()
        .getDirectDependencies(MODULES)
        .uniqueModules()
        .map(ModularityTest::name)
        .collect(Collectors.toSet());
  }

  private static String name(ApplicationModule module) {
    return module.getIdentifier().toString();
  }
}
