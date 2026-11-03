package co.com.sapenlinea.liv.platform.navigation;

import java.util.List;

/**
 * A menu option the user sees (001-15): its number in Doc. 01 §3, whether it is built, and the
 * options under it that the user also sees.
 */
public record Capability(String code, CapabilityStatus status, List<Capability> children) {

  public Capability {
    children = List.copyOf(children);
  }
}
