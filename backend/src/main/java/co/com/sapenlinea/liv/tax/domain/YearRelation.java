package co.com.sapenlinea.liv.tax.domain;

/** How a year that can be created relates to the current year (hour of Colombia). */
public enum YearRelation {
  /** A year before the current one. */
  PAST,
  /** The current year. */
  CURRENT,
  /** The year after the current one. */
  NEXT
}
