package co.com.sapenlinea.liv.tax.domain;

/**
 * The state of a point of the preparation checklist. "Optional" is not a state: it is the {@link
 * PreparationPoint#required()} flag. Nobody returns {@link #OBSERVED} yet: the first feature whose
 * point can have an observation adds it (plan, step 5).
 */
public enum PreparationItemStatus {
  MET,
  PENDING,
  OBSERVED
}
