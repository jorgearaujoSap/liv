package co.com.sapenlinea.liv.tax.domain;

/**
 * State of a fiscal year in an administration (Doc. 01 §5). The names are the codes of the
 * document. Feature 007 only allows {@code PLANEADA} to {@code EN_PREPARACION}; the other
 * transitions arrive with the feature of option 1.5.1.
 */
public enum FiscalYearStatus {
  PLANEADA,
  EN_PREPARACION,
  LISTA,
  HABILITADA,
  CERRADA
}
