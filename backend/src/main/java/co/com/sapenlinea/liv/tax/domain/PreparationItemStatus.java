package co.com.sapenlinea.liv.tax.domain;

/**
 * The state of a point of the preparation checklist, as {@code PreparationItemStatus} in {@code
 * contracts/openapi/tax.yaml}. "Optional" is not a state: it is the {@link
 * PreparationPoint#required()} flag. {@link #OBSERVED} exists in the contract, but no point returns
 * it yet: the first feature whose point can have an observation will.
 */
public enum PreparationItemStatus {
  MET,
  PENDING,
  OBSERVED
}
