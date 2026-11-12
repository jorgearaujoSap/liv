package co.com.sapenlinea.liv.ledger.golden;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the ledger is given for a golden case: only what the functional document takes for granted.
 * Amounts are exact decimals, read with {@code decimalValue()}, never as {@code double}. Dates and
 * tax values (discount deadline, due date, rounding) are not here: they come from the jurisdiction
 * configuration.
 *
 * <p>A case starts from exactly one of three forms: an {@link Emission} event, {@code
 * openingMovements} or an {@link Allocation} event. The modality of the obligation is always an
 * input, given once. With an emission it travels in the event, because the liquidation decided it.
 * With an allocation it travels in the {@code obligation}. With opening movements it is not used.
 *
 * @param obligation the origin modality and the declared address, for the cases that distribute
 *     (null otherwise)
 * @param openingMovements the debt the case starts from, by component, for the cases without an
 *     emission (empty otherwise)
 * @param events what happens, in order
 */
record LedgerGoldenInput(
    Obligation obligation, List<Movement> openingMovements, List<Event> events) {

  /**
   * The obligation of a case that starts from an imputation.
   *
   * @param originModality {@code UEDF_INTERNACION} or {@code CONVENIO_DEPTO}
   * @param declaredAddress the municipality of the declared address
   */
  record Obligation(String originModality, DeclaredAddress declaredAddress) {}

  /**
   * The declared address, compared by the adapter with the jurisdiction of the configuration: no
   * municipality code is invented.
   *
   * @param sameAsJurisdiction true if it is the municipality of the configuration (Ipiales)
   * @param name the municipality when it is another one (null when it is the same)
   */
  record DeclaredAddress(boolean sameAsJurisdiction, String name) {}

  /** A ledger movement given by the case: its type, its component and its signed amount. */
  record Movement(String type, String component, BigDecimal signedAmount) {}

  /** Something that happens to the obligation. */
  sealed interface Event permits Emission, Payment, BankReversal, Allocation {}

  /**
   * The emission of a liquidation (Doc. 04 section 3.1), with the modality that the liquidation
   * decided. The obligation takes its modality from here.
   *
   * @param netTax the rounded net tax, without subtracting the discount
   * @param netTaxWithDiscount the rounded amount to pay with the discount
   * @param originModality {@code UEDF_INTERNACION} or {@code CONVENIO_DEPTO}
   */
  record Emission(BigDecimal netTax, BigDecimal netTaxWithDiscount, String originModality)
      implements Event {}

  /**
   * A payment already reconciled.
   *
   * @param date the date of the payment
   * @param amount the amount paid
   * @param status always {@code CONCILIADO}: the reconciliation is outside the boundary
   */
  record Payment(LocalDate date, BigDecimal amount, String status) implements Event {}

  /** The bank reverses the payment (Doc. 04 section 6.2). */
  record BankReversal() implements Event {}

  /**
   * The imputation of a reconciled payment, already made, with its movements (the distribution is
   * made for each imputation, Doc. 04 section 7).
   *
   * @param paymentStatus always {@code CONCILIADO}
   * @param paymentAmount the amount of the payment
   * @param movements the movements of the imputation
   */
  record Allocation(String paymentStatus, BigDecimal paymentAmount, List<Movement> movements)
      implements Event {}
}
