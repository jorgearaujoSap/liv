package co.com.sapenlinea.liv.tax.domain;

/** How a year that can be created relates to the current year (hour of Colombia). */
public enum YearRelation {
  /** A past year that is still pending: for example 2027 when the latest is 2026 and it is 2028. */
  PAST,
  /** The current year. */
  CURRENT,
  /** The year after the current one. */
  NEXT
}
