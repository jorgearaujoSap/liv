package co.com.sapenlinea.liv.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.api.contract.model.Capability;
import co.com.sapenlinea.liv.platform.api.contract.model.CapabilityStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The mapping to the contract of {@code GET /api/v1/me/capabilities} (001-15) with both statuses:
 * the LIV menu has only COMING_SOON options for now, so the API tests never see AVAILABLE.
 */
@DisplayName("Capacidades: conversión al contrato (T14)")
class CapabilitiesControllerTest {

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Código, estado e hijos pasan al contrato tal cual, en su orden")
  void mapsCodeStatusAndChildren() {
    List<co.com.sapenlinea.liv.platform.navigation.Capability> menu =
        List.of(
            new co.com.sapenlinea.liv.platform.navigation.Capability(
                "1",
                co.com.sapenlinea.liv.platform.navigation.CapabilityStatus.AVAILABLE,
                List.of(
                    new co.com.sapenlinea.liv.platform.navigation.Capability(
                        "1.4",
                        co.com.sapenlinea.liv.platform.navigation.CapabilityStatus.AVAILABLE,
                        List.of()),
                    new co.com.sapenlinea.liv.platform.navigation.Capability(
                        "1.3",
                        co.com.sapenlinea.liv.platform.navigation.CapabilityStatus.COMING_SOON,
                        List.of()))));

    assertThat(CapabilitiesController.toContractList(menu))
        .containsExactly(
            new Capability(
                "1",
                CapabilityStatus.AVAILABLE,
                List.of(
                    new Capability("1.4", CapabilityStatus.AVAILABLE, List.of()),
                    new Capability("1.3", CapabilityStatus.COMING_SOON, List.of()))));
  }
}
