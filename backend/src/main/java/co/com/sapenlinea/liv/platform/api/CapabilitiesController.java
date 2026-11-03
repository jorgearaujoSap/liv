package co.com.sapenlinea.liv.platform.api;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.api.contract.CapabilitiesApi;
import co.com.sapenlinea.liv.platform.api.contract.model.Capabilities;
import co.com.sapenlinea.liv.platform.api.contract.model.Capability;
import co.com.sapenlinea.liv.platform.api.contract.model.CapabilityStatus;
import co.com.sapenlinea.liv.platform.navigation.GetCapabilities;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/me/capabilities} (platform.yaml, 001-15): the menu of the user. */
@RestController
class CapabilitiesController implements CapabilitiesApi {

  private final GetCapabilities getCapabilities;

  CapabilitiesController(GetCapabilities getCapabilities) {
    this.getCapabilities = getCapabilities;
  }

  @Override
  public ResponseEntity<Capabilities> getCapabilities() {
    CurrentUser user =
        CurrentUser.current()
            .orElseThrow(() ->
                new AuthenticationRejectedException(
                    ErrorCodes.NOT_AUTHENTICATED, "No current user"));
    return ResponseEntity.ok(new Capabilities(toContractList(getCapabilities.of(user))));
  }

  // Two types share the name Capability: the generated model (imported) and the one of
  // platform.navigation (written in full), which the controller maps to the model.
  static List<Capability> toContractList(
      List<co.com.sapenlinea.liv.platform.navigation.Capability> capabilities) {
    return capabilities.stream().map(CapabilitiesController::toContract).toList();
  }

  private static Capability toContract(
      co.com.sapenlinea.liv.platform.navigation.Capability capability) {
    return new Capability(
        capability.code(),
        CapabilityStatus.valueOf(capability.status().name()),
        toContractList(capability.children()));
  }
}
